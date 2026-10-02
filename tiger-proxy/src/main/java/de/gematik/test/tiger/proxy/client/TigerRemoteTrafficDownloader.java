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
import de.gematik.rbellogger.data.RbelMessageMetadata;
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

    final boolean complete = downloadAllTrafficFromRemote();

    if (complete) {
      log.info(
          "Successfully downloaded & parsed missed traffic from '{}'. Now {} message(s)"
              + " in local history ({} actual messages)",
          getRemoteProxyUrl(),
          getRbelLogger().getMessages().size(),
          getRbelLogger().getMessagesByOrder().size());
    } else {
      log.warn(
          "Incompletely downloaded missed traffic from '{}'. Now {} message(s)"
              + " in local history ({} actual messages)",
          getRemoteProxyUrl(),
          getRbelLogger().getMessages().size(),
          getRbelLogger().getMessagesByOrder().size());
    }
  }

  @SneakyThrows
  private void parseTrafficChunk(InputStream rawTraffic) {
    final List<RbelElement> convertedMessages =
        tigerRemoteProxyClient
            .getRbelFileReader()
            .convertRbelFileEntries(
                new BufferedReader(new InputStreamReader(rawTraffic)).lines(),
                Optional.empty(),
                this::downloadMessageContent,
                this::markAsDownloadedFromRemote);

    doMessageBatchPostProcessing(convertedMessages);
  }

  private void markAsDownloadedFromRemote(RbelElement element, RbelMessageMetadata metadata) {
    element.addFacet(new TigerDownloadedMessageFacet());
    ClockSkewEstimator.applyCompensation(metadata, tigerRemoteProxyClient.getRemoteClockOffset());
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
    convertedMessages.forEach(this::addRemoteUrlToTcpIpFacet);
    if (log.isTraceEnabled()) {
      log.trace(
          "Just parsed another traffic batch, got {} messages. Now standing at {} messages overall",
          convertedMessages.size(),
          getRbelLogger().getMessages().size());
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

  /**
   * Downloads the history of the upstream proxy, page by page.
   *
   * @return false if we gave up while the upstream still had messages left
   */
  private boolean downloadAllTrafficFromRemote() {
    PaginationInfo paginationInfo;
    int pageNumber = 0;
    // we make a copy of the last uuid because the traffic parsing will be commenced in parallel,
    // meaning the tigerRemoteProxyClient.getLastMessageUuid() can shift
    Optional<String> currentLastUuid =
        Optional.ofNullable(tigerRemoteProxyClient.getLastMessageUuid().get());
    final var configuration = tigerRemoteProxyClient.getTigerProxyConfiguration();
    final int pageSize = configuration.getTrafficDownloadPageSize();
    final int maximumPages = configuration.getMaximumTrafficDownloadPages();
    do {
      paginationInfo = downloadTrafficPageFromRemoteAndAddToQueue(pageSize, currentLastUuid);
      pageNumber++;

      final Optional<String> nextLastUuid =
          Optional.ofNullable(paginationInfo.getLastUuid()).filter(StringUtils::isNotEmpty);
      if (nextLastUuid.isEmpty() || nextLastUuid.equals(currentLastUuid)) {
        // nothing new: paging on would re-request this page, or restart from the beginning
        log.atDebug()
            .addArgument(pageNumber)
            .addArgument(this::getRemoteProxyUrl)
            .log("Traffic-download finished after {} page(s), '{}' has no further messages");
        return true;
      }
      currentLastUuid = nextLastUuid;

      // available-messages counts the page we just took, so what is left is what the loop
      // condition below asks about - and the brake must not fire on a page that completed the
      // history just because it happened to be the last one we were allowed to fetch
      final int stillPending = paginationInfo.getAvailableMessages() - pageSize;
      if (stillPending > 0 && pageNumber >= maximumPages) {
        log.error(
            "Giving up on the traffic-download from '{}' after {} pages of {} message(s): the"
                + " remote still reports {} pending message(s). Those messages are MISSING from the"
                + " local history and will not be pushed later on. Consider raising"
                + " tigerProxy.maximumTrafficDownloadPages.",
            getRemoteProxyUrl(),
            pageNumber,
            pageSize,
            stillPending);
        return false;
      }
    } while (paginationInfo.getAvailableMessages() > pageSize);
    return true;
  }

  private PaginationInfo downloadTrafficPageFromRemoteAndAddToQueue(
      int pageSize, Optional<String> currentLastUuid) {
    final String downloadUrl = getRemoteProxyUrl() + "/webui/trafficLog.tgr";
    log.atDebug()
        .addArgument(downloadUrl)
        .addArgument(() -> currentLastUuid.orElse(""))
        .addArgument(pageSize)
        .addArgument(() -> getRbelLogger().getMessages().size())
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
