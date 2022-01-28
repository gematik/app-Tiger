package de.gematik.test.tiger.proxy.tracing;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.client.TigerExceptionDto;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import de.gematik.test.tiger.proxy.client.TigerTracingDto;
import de.gematik.test.tiger.proxy.client.TracingMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
@Slf4j
public class TracingPushController {

    public final SimpMessagingTemplate template;
    public final TigerProxy tigerProxy;

    @PostConstruct
    public void addWebSocketListener() {
        tigerProxy.addRbelMessageListener(msg -> propagateRbelMessageSafe(msg));
        tigerProxy.addNewExceptionConsumer(exc -> propagateExceptionSafe(exc));
    }

    private void propagateExceptionSafe(Throwable exc) {
        try {
            propagateException(exc);
        } catch (RuntimeException e) {
            log.error("Error while propagating Exception", e);
            throw e;
        }
    }

    private void propagateRbelMessageSafe(RbelElement msg) {
        try {
            propagateRbelMessage(msg);
        } catch (RuntimeException e) {
            log.error("Error while propagating new Rbel-Message", e);
            throw e;
        }
    }

    private void propagateRbelMessage(RbelElement msg) {
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
        template.convertAndSend(TigerRemoteProxyClient.WS_TRACING,
            TigerTracingDto.builder()
                .uuid(msg.getUuid())
                .receiver(receiver)
                .sender(sender)
                .response(mapMessage(msg))
                .request(mapMessage(rbelHttpResponse.getRequest()))
                .build());
    }

    private void propagateException(Throwable exception) {
        template.convertAndSend(TigerRemoteProxyClient.WS_ERRORS,
            TigerExceptionDto.builder()
                .className(exception.getClass().getName())
                .message(exception.getMessage())
                .stacktrace(ExceptionUtils.getStackTrace(exception))
                .build()
        );
    }

    private TracingMessage mapMessage(RbelElement rbelHttpMessage) {
        if (rbelHttpMessage == null) {
            return TracingMessage.builder()
                .build();
        }
        return TracingMessage.builder()
            .rawContent(rbelHttpMessage.getRawContent())
            .build();
    }
}
