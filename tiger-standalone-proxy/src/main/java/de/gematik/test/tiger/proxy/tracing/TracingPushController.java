package de.gematik.test.tiger.proxy.tracing;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.test.tiger.proxy.TigerProxy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class TracingPushController {

    public final SimpMessagingTemplate template;
    public final TigerProxy tigerProxy;

    @PostConstruct
    public void addWebSocketListener() {
        tigerProxy.addRbelMessageListener(msg -> {
            if (!msg.hasFacet(RbelHttpResponseFacet.class)
                    || !msg.hasFacet(RbelHttpMessageFacet.class)
                    || !msg.hasFacet(RbelTcpIpMessageFacet.class)) {
                log.trace("Skipping propagation, not a response");
                return;
            }
            log.debug("Handling Rbel-Message!");
            RbelHttpResponseFacet rbelHttpResponse = msg.getFacetOrFail(RbelHttpResponseFacet.class);
            RbelTcpIpMessageFacet rbelTcpIpMessageFacet = msg.getFacetOrFail(RbelTcpIpMessageFacet.class);
            final RbelHostname sender = rbelTcpIpMessageFacet.getSender().seekValue(RbelHostname.class).orElse(null);
            final RbelHostname receiver = rbelTcpIpMessageFacet.getReceiver().seekValue(RbelHostname.class).orElse(null);
            log.info("Propagating new request/response pair (from {} to {}, path {}, status {})",
                    sender, receiver,
                    rbelHttpResponse.getRequest().getFacetOrFail(RbelHttpRequestFacet.class)
                            .getPath().getRawStringContent(),
                    rbelHttpResponse.getResponseCode().getRawStringContent());
            template.convertAndSend("/topic/traces",
                    TigerTracingDto.builder()
                            .uuid(msg.getUuid())
                            .receiver(receiver)
                            .sender(sender)
                            .response(mapMessage(msg))
                            .request(mapMessage(rbelHttpResponse.getRequest()))
                            .build());
        });
    }

    private TracingMessage mapMessage(RbelElement rbelHttpMessage) {
        return TracingMessage.builder()
                .rawContent(rbelHttpMessage.getRawContent())
                .build();
    }
}
