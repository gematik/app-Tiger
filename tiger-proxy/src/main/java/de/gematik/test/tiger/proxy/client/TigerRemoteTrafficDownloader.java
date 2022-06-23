/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.proxy.client;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.util.RbelFileWriterUtils;
import de.gematik.test.tiger.proxy.data.TigerDownloadedMessageFacet;
import de.gematik.test.tiger.proxy.data.TracingMessagePairFacet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;

@AllArgsConstructor
@Slf4j
public class TigerRemoteTrafficDownloader {

    private final TigerRemoteProxyClient tigerRemoteProxyClient;

    public void execute() {
        tigerRemoteProxyClient.getTrafficParserExecutor()
            .submit(tigerRemoteProxyClient::switchToQueueMode);

        downloadAllTrafficFromRemote();

        log.info("{}Successfully downloaded missed traffic from '{}'.",
            tigerRemoteProxyClient.proxyName(), getRemoteProxyUrl());

        tigerRemoteProxyClient.getTrafficParserExecutor()
            .submit(() -> log.info("{}Successfully downloaded & parsed missed traffic from '{}'. Now {} message cached",
                tigerRemoteProxyClient.proxyName(), getRemoteProxyUrl(), getRbelMessages().size()));

        tigerRemoteProxyClient.getTrafficParserExecutor()
            .submit(tigerRemoteProxyClient::switchToExecutorMode);
    }

    private void parseTrafficChunk(String rawTraffic) {
        final List<RbelElement> convertedMessages = RbelFileWriterUtils.convertFromRbelFile(
            rawTraffic, getRbelLogger().getRbelConverter());
        final long count = rawTraffic.lines().count();
        convertedMessages.forEach(msg -> msg.addFacet(new TigerDownloadedMessageFacet()));
        for (int i = 0; i < convertedMessages.size(); i += 2) {
            val pairFacet = TracingMessagePairFacet.builder()
                .response(convertedMessages.get(i + 1))
                .request(convertedMessages.get(i))
                .build();
            convertedMessages.get(i + 1).addFacet(pairFacet);
        }
        if (log.isTraceEnabled()) {
            log.trace(
                "{}Just parsed another traffic batch of {} lines, got {} messages, expected {} (rest was filtered). Now standing at {} messages overall",
                tigerRemoteProxyClient.proxyName(), count, convertedMessages.size(), (count + 2) / 3,
                getRbelMessages().size());
        }
        if (!convertedMessages.isEmpty()) {
            tigerRemoteProxyClient.getLastMessageUuid().set(
                convertedMessages.get(convertedMessages.size() - 1).getUuid()
            );
        }
        convertedMessages.forEach(tigerRemoteProxyClient::triggerListener);
        if (log.isTraceEnabled()) {
            log.trace("{}Parsed traffic, ending with {}", tigerRemoteProxyClient.proxyName(),
                convertedMessages.stream()
                    .map(RbelElement::getRawStringContent)
                    .flatMap(content -> Stream.of(content.split(" ")).skip(1).limit(1))
                    .filter(httpHeaderString -> httpHeaderString.startsWith("/"))
                    .collect(Collectors.joining(", ")));
        }
    }

    private void downloadAllTrafficFromRemote() {
        PaginationInfo paginationInfo;
        int pageNumber = 0;
        // we make a copy of the last uuid because the traffic parsing will be commenced in parallel, meaning
        // the tigerRemoteProxyClient.getLastMessageUuid() can shift
        Optional<String> currentLastUuid = Optional.ofNullable(tigerRemoteProxyClient.getLastMessageUuid().get());
        final int pageSize = tigerRemoteProxyClient.getTigerProxyConfiguration().getTrafficDownloadPageSize();
        do {
            paginationInfo = downloadTrafficPageFromRemoteAndAddToQueue(pageSize, currentLastUuid);
            pageNumber++;
            currentLastUuid = Optional.ofNullable(paginationInfo.getLastUuid())
                .filter(StringUtils::isNotEmpty);

            if (pageNumber > 100) {
                log.warn("Interrupting traffic-download: Reached 100 downloads! "
                    + "(Maybe the influx of traffic on the upstream proxy is greater then our downstream-sped?)");
                return;
            }
        } while (paginationInfo.getAvailableMessages() > pageSize);
    }

    private PaginationInfo downloadTrafficPageFromRemoteAndAddToQueue(int pageSize, Optional<String> currentLastUuid) {
        final String downloadUrl = getRemoteProxyUrl() + "/webui/trafficLog.tgr";
        log.debug(
            "{}Downloading missed traffic from '{}', starting from {}. page-size {} (currently cached {} messages)",
            tigerRemoteProxyClient.proxyName(), currentLastUuid, downloadUrl, pageSize, getRbelMessages().size());

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("pageSize", pageSize);
        currentLastUuid
            .ifPresent(uuid -> parameters.put("lastMsgUuid", uuid));

        final HttpResponse<String> response = Unirest.get(downloadUrl)
            .queryString(parameters)
            .asString();
        if (response.getStatus() != 200) {
            throw new TigerRemoteProxyClientException(
                "Error while downloading message from remote '" + downloadUrl + "': " + response.getBody());
        }
        tigerRemoteProxyClient.getTrafficParserExecutor().submit(() -> parseTrafficChunk(response.getBody()));
        return PaginationInfo.of(response);
    }

    private RbelLogger getRbelLogger() {
        return tigerRemoteProxyClient.getRbelLogger();
    }

    private List<RbelElement> getRbelMessages() {
        return tigerRemoteProxyClient.getRbelMessages();
    }

    private String getRemoteProxyUrl() {
        return tigerRemoteProxyClient.getRemoteProxyUrl();
    }

    @Data
    @Builder
    private static class PaginationInfo {

        private final int availableMessages;
        private final String lastUuid;

        public static PaginationInfo of(HttpResponse<String> response) {
            return PaginationInfo.builder()
                .availableMessages(convertHeaderFieldToInt(response, "available-messages"))
                .lastUuid(response.getHeaders().getFirst("last-uuid"))
                .build();
        }

        private static Integer convertHeaderFieldToInt(HttpResponse<String> response, String key) {
            return Optional.ofNullable(response.getHeaders().getFirst(key))
                .filter(StringUtils::isNotEmpty)
                .map(Integer::parseInt)
                .orElse(-1);
        }
    }
}
