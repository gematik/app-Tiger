package de.gematik.test.tiger.proxy.controller;

import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.test.tiger.proxy.TigerProxy;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class TrafficPushController {

    public static final String SENDER_REQUEST_HEADER = "tgr-sender";
    public static final String RECEIVER_REQUEST_HEADER = "tgr-receiver";
    public static final String TIMESTAMP_REQUEST_HEADER = "tgr-timestamp";

    private final TigerProxy tigerProxy;

    @PostMapping(value = "/traffic")
    public void postNewMessage(InputStream dataStream,
        @RequestHeader(SENDER_REQUEST_HEADER) final Optional<String> sender,
        @RequestHeader(RECEIVER_REQUEST_HEADER) final Optional<String> receiver,
        @RequestHeader(TIMESTAMP_REQUEST_HEADER) final Optional<String> timestamp) throws IOException {
        tigerProxy.getRbelLogger().getRbelConverter()
            .parseMessage(IOUtils.toByteArray(dataStream),
                sender.flatMap(RbelHostname::fromString).orElse(null),
                receiver.flatMap(RbelHostname::fromString).orElse(null),
                timestamp.map(ZonedDateTime::parse));
    }
}
