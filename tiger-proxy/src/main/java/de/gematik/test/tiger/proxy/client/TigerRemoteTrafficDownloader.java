/*
 *
 * Copyright 2021-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.proxy.client;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.util.RbelContent;
import de.gematik.test.tiger.proxy.controller.TigerWebUiController;
import de.gematik.test.tiger.proxy.data.TigerDownloadedMessageFacet;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.RawResponse;
import kong.unirest.core.Unirest;
import lombok.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
public class TigerRemoteTrafficDownloader {

  private final TigerRemoteProxyClient tigerRemoteProxyClient;
  private Logger log = LoggerFactory.getLogger(TigerRemoteTrafficDownloader.class);

  public void execute() {
    log =
        LoggerFactory.getLogger(
            TigerRemoteTrafficDownloader.class.getName()
                + "("
                + tigerRemoteProxyClient.proxyName()
                + ")");

    downloadAllTrafficFromRemote();

    log.info(
        "Successfully downloaded & parsed missed traffic from '{}'. Now {} message(s)"
            + " in local history ({} actual messages)",
        getRemoteProxyUrl(),
        getRbelLogger().getMessageHistory().size(),
        getRbelLogger().getMessageList().size());
  }

  @SneakyThrows
  private void parseTrafficChunk(InputStream rawTraffic) {
    final List<RbelElement> convertedMessages =
        tigerRemoteProxyClient
            .getRbelFileWriter()
            .convertRbelFileEntries(
                new BufferedReader(new InputStreamReader(rawTraffic)).lines(),
                Optional.empty(),
                this::downloadMessageContent);

    doMessageBatchPostProcessing(convertedMessages);
  }

  @SneakyThrows
  private RbelContent downloadMessageContent(String uuid) {

    final String downloadUrl = getRemoteProxyUrl() + "/webui/messageContent/" + uuid;
    log.trace("Downloading content of message from '{}' with uuid '{}'", downloadUrl, uuid);

    try {
      final HttpResponse<InputStream> response =
          Unirest.get(downloadUrl).asObject(RawResponse::getContent);
      log.trace(
          "Downloaded traffic from remote '{}', status: {}", downloadUrl, response.getStatus());
      if (response.getStatus() != 200) {
        throw new TigerRemoteProxyClientException(
            "Error while downloading message from remote '" + downloadUrl);
      }
      return RbelContent.from(response.getBody());
    } catch (OutOfMemoryError error) {
      log.error(
          "OutOfMemoryError while downloading traffic from remote '{}'. "
              + "Please increase the heap size of the Tiger Proxy.",
          downloadUrl,
          error);
      throw error;
    } catch (IOException e) {
      log.error("IOException while downloading traffic from remote '{}'", getRemoteProxyUrl(), e);
      throw e;
    }
  }

  private void doMessageBatchPostProcessing(List<RbelElement> convertedMessages) {
    convertedMessages.forEach(
        msg -> {
          msg.addFacet(new TigerDownloadedMessageFacet());
          addRemoteUrlToTcpIpFacet(msg);
        });
    if (log.isTraceEnabled()) {
      log.trace(
          "Just parsed another traffic batch, got {} messages. Now standing at {} messages overall",
          convertedMessages.size(),
          getRbelLogger().getMessageHistory().size());
    }
    if (!convertedMessages.isEmpty()) {
      tigerRemoteProxyClient
          .getLastMessageUuid()
          .set(convertedMessages.get(convertedMessages.size() - 1).getUuid());
    }
    if (log.isTraceEnabled()) {
      log.trace(
          "Parsed traffic, ending with {}",
          convertedMessages.stream()
              .map(RbelElement::getRawStringContent)
              .flatMap(content -> Stream.of(content.split(" ")).skip(1).limit(1))
              .filter(httpHeaderString -> httpHeaderString.startsWith("/"))
              .collect(Collectors.joining(", ")));
    }
  }

  private void addRemoteUrlToTcpIpFacet(RbelElement element) {
    element
        .getFacet(RbelTcpIpMessageFacet.class)
        .map(f -> f.toBuilder().receivedFromRemoteWithUrl(getRemoteProxyUrl()).build())
        .ifPresent(element::addOrReplaceFacet);
  }

  private void downloadAllTrafficFromRemote() {
    PaginationInfo paginationInfo;
    int pageNumber = 0;
    // we make a copy of the last uuid because the traffic parsing will be commenced in parallel,
    // meaning the tigerRemoteProxyClient.getLastMessageUuid() can shift
    Optional<String> currentLastUuid =
        Optional.ofNullable(tigerRemoteProxyClient.getLastMessageUuid().get());
    final int pageSize =
        tigerRemoteProxyClient.getTigerProxyConfiguration().getTrafficDownloadPageSize();
    do {
      paginationInfo = downloadTrafficPageFromRemoteAndAddToQueue(pageSize, currentLastUuid);
      pageNumber++;
      currentLastUuid =
          Optional.ofNullable(paginationInfo.getLastUuid()).filter(StringUtils::isNotEmpty);

      if (pageNumber > 100) {
        log.warn(
            "Interrupting traffic-download: Reached 100 downloads! (Maybe the influx of traffic on"
                + " the upstream proxy is greater then our downstream-sped?)");
        return;
      }
    } while (paginationInfo.getAvailableMessages() > pageSize);
  }

  private PaginationInfo downloadTrafficPageFromRemoteAndAddToQueue(
      int pageSize, Optional<String> currentLastUuid) {
    final String downloadUrl = getRemoteProxyUrl() + "/webui/trafficLog.tgr";
    log.atDebug()
        .addArgument(downloadUrl)
        .addArgument(() -> currentLastUuid.orElse(""))
        .addArgument(pageSize)
        .addArgument(() -> getRbelLogger().getMessageHistory().size())
        .log(
            "Downloading missed traffic from '{}', starting after {}. page-size {} (currently"
                + " cached {} messages)");

    final Map<String, Object> parameters = new HashMap<>();
    parameters.put("pageSize", pageSize);
    parameters.put("includeVersion", false);
    parameters.put("skipContentThreshold", TigerWebUiController.SKIP_CONTENT_THRESHOLD);
    currentLastUuid.ifPresent(uuid -> parameters.put("lastMsgUuid", uuid));

    try {
      final HttpResponse<InputStream> response =
          Unirest.get(downloadUrl).queryString(parameters).asObject(RawResponse::getContent);
      log.atTrace()
          .addArgument(downloadUrl)
          .addArgument(response::getStatus)
          .log("Downloaded traffic from remote '{}', status: {}");
      if (response.getStatus() != 200) {
        throw new TigerRemoteProxyClientException(
            "Error while downloading message from remote '"
                + downloadUrl
                + "': "
                + response.getBody());
      }
      parseTrafficChunk(response.getBody());
      return PaginationInfo.of(response);
    } catch (OutOfMemoryError error) {
      log.error(
          "OutOfMemoryError while downloading traffic from remote '{}'. "
              + "Please increase the heap size of the Tiger Proxy.",
          getRemoteProxyUrl(),
          error);
      throw error;
    }
  }

  private RbelLogger getRbelLogger() {
    return tigerRemoteProxyClient.getRbelLogger();
  }

  private String getRemoteProxyUrl() {
    return tigerRemoteProxyClient.getRemoteProxyUrl();
  }

  @Data
  @Builder
  private static class PaginationInfo {

    private final int availableMessages;
    private final String lastUuid;

    public static PaginationInfo of(HttpResponse<?> response) {
      return PaginationInfo.builder()
          .availableMessages(convertHeaderFieldToInt(response, "available-messages"))
          .lastUuid(response.getHeaders().getFirst("last-uuid"))
          .build();
    }

    private static Integer convertHeaderFieldToInt(HttpResponse<?> response, String key) {
      return java.util.Optional.ofNullable(response.getHeaders().getFirst(key))
          .filter(StringUtils::isNotEmpty)
          .map(Integer::parseInt)
          .orElse(-1);
    }
  }
}
