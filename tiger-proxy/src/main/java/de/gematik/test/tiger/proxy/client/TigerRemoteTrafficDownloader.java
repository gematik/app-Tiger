/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.client;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.util.RbelFileWriterUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import kong.unirest.HttpResponse;
import kong.unirest.RequestBodyEntity;
import kong.unirest.Unirest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@RequiredArgsConstructor
@Slf4j
public class TigerRemoteTrafficDownloader {

    private final TigerRemoteProxyClient tigerRemoteProxyClient;
    private final AtomicReference<String> lastMessageUuid;

    public void execute() {
        final String rawTraffic = downloadTrafficFromRemote();
        log.info("Downloaded {} of traffic. Now parsing...",
            FileUtils.byteCountToDisplaySize(rawTraffic.length()));

        parseDownloadedTraffic(rawTraffic);
        log.info("Successfully downloaded missed traffic from '{}'. Now {} message cached",
            getRemoteProxyUrl(), getRbelMessages().size());

    }

    private void parseDownloadedTraffic(String rawTraffic) {
        final List<RbelElement> convertedMessages = RbelFileWriterUtils.convertFromRbelFile(
            rawTraffic, getRbelLogger().getRbelConverter());

        lastMessageUuid.compareAndSet(null, convertedMessages.get(convertedMessages.size() - 1).getUuid());
    }

    private String downloadTrafficFromRemote() {
        final String downloadUrl = getRemoteProxyUrl() + "/webui/trafficLog.tgr";
        log.info("Downloading missed traffic from '{}' (currently cached {} messages)",
            downloadUrl, getRbelMessages().size());
        final Map<String, Object> parameters = new HashMap<>();
        Optional.ofNullable(lastMessageUuid.get())
            .ifPresent(uuid -> parameters.put("lastMsgUuid", uuid));

        final HttpResponse<String> response = Unirest.get(downloadUrl)
            .queryString(parameters)
            .asString();
        if (response.getStatus() != 200) {
            throw new TigerRemoteProxyClientException(
                "Error while downloading message from remote '" + downloadUrl + "': " + response.getBody());
        }
        return response.getBody();
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
}
