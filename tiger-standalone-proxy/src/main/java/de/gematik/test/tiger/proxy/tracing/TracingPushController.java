package de.gematik.test.tiger.proxy.tracing;

import de.gematik.rbellogger.data.elements.RbelHttpMessage;
import de.gematik.rbellogger.data.elements.RbelHttpRequest;
import de.gematik.rbellogger.data.elements.RbelHttpResponse;
import de.gematik.test.tiger.proxy.TigerProxy;
import java.nio.charset.StandardCharsets;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TracingPushController {

    public final SimpMessagingTemplate template;
    public final TigerProxy tigerProxy;

    @PostConstruct
    public void addWebSocketListener() {
        tigerProxy.addRbelMessageListener(msg -> {
            log.debug("Handling Rbel-Message!");
            if (msg.getHttpMessage() instanceof RbelHttpRequest) {
                log.trace("Skipping propagation of request");
                return;
            }
            RbelHttpResponse rbelHttpResponse = (RbelHttpResponse) msg.getHttpMessage();
            log.info("Propagating new request/response pair (from {} to {}, path {}, status {})",
                    msg.getSender(), msg.getRecipient(),
                    rbelHttpResponse.getRequest().getPath().getOriginalUrl(),
                    rbelHttpResponse.getResponseCode());
            template.convertAndSend("/topic/traces",
                TigerTracingDto.builder()
                    .uuid(msg.getUuid())
                    .receiver(msg.getRecipient())
                    .sender(msg.getSender())
                    .response(mapMessage(rbelHttpResponse))
                    .request(mapMessage(rbelHttpResponse.getRequest()))
                    .build());
        });
    }

    private TracingMessage mapMessage(RbelHttpMessage rbelHttpMessage) {
        return TracingMessage.builder()
            .header(rbelHttpMessage.getRawMessage().split("\n\n",2)[0])
            .body(rbelHttpMessage.getRawBody())
            .build();
    }
}
