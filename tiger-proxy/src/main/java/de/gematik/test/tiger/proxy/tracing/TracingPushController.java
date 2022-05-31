/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.tracing;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.client.TigerExceptionDto;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import de.gematik.test.tiger.proxy.client.TigerTracingDto;
import de.gematik.test.tiger.proxy.client.TracingMessagePart;
import de.gematik.test.tiger.proxy.data.TracingMessagePairFacet;
import java.util.Arrays;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TracingPushController {

    public static final int MAX_MESSAGE_SIZE = 512 * 1024;
    public final SimpMessagingTemplate template;
    public final TigerProxy tigerProxy;

    @PostConstruct
    public void addWebSocketListener() {
        tigerProxy.addRbelMessageListener(this::propagateRbelMessageSafe);
        tigerProxy.addNewExceptionConsumer(this::propagateExceptionSafe);
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
        if (!msg.hasFacet(RbelTcpIpMessageFacet.class)) {
            log.trace("Skipping propagation, not a TCP/IP message");
            return;
        }
        if (!msg.hasFacet(RbelHttpResponseFacet.class)
            && !msg.hasFacet(TracingMessagePairFacet.class)) {
            log.trace("Skipping propagation, not a response");
            return;
        }
        log.trace("Handling Rbel-Message!");
        RbelTcpIpMessageFacet rbelTcpIpMessageFacet = msg.getFacetOrFail(RbelTcpIpMessageFacet.class);
        final RbelHostname sender = rbelTcpIpMessageFacet.getSender().seekValue(RbelHostname.class).orElse(null);
        final RbelHostname receiver = rbelTcpIpMessageFacet.getReceiver().seekValue(RbelHostname.class).orElse(null);
        final RbelElement request = msg.getFacet(TracingMessagePairFacet.class)
            .map(TracingMessagePairFacet::getRequest)
            .or(() -> msg.getFacet(RbelHttpResponseFacet.class)
                .map(RbelHttpResponseFacet::getRequest))
            .orElseThrow();

        log.debug("Propagating new request/response pair (from {} to {}, path {}, status {})",
            sender, receiver,
            msg.getFacet(RbelHttpResponseFacet.class)
                .map(RbelHttpResponseFacet::getRequest)
                .map(req -> req.getFacetOrFail(RbelHttpRequestFacet.class))
                .map(RbelHttpRequestFacet::getPath)
                .map(RbelElement::getRawStringContent)
                .orElse("<unknown>"),
            msg.getFacet(RbelHttpResponseFacet.class)
                .map(RbelHttpResponseFacet::getResponseCode)
                .map(RbelElement::getRawStringContent)
                .orElse("<unknown>"));
        template.convertAndSend(TigerRemoteProxyClient.WS_TRACING,
            TigerTracingDto.builder()
                .uuid(msg.getUuid())
                .receiver(receiver)
                .sender(sender)
                .responseUuid(msg.getUuid())
                .requestUuid(request.getUuid())
                .responseTransmissionTime(msg.getFacet(RbelMessageTimingFacet.class)
                    .map(RbelMessageTimingFacet::getTransmissionTime)
                    .orElse(null))
                .requestTransmissionTime(request.getFacet(RbelMessageTimingFacet.class)
                    .map(RbelMessageTimingFacet::getTransmissionTime)
                    .orElse(null))
                .build());

        mapRbelMessageAndSent(msg);
        mapRbelMessageAndSent(request);
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

    private void mapRbelMessageAndSent(RbelElement rbelHttpMessage) {
        if (rbelHttpMessage == null) {
            return;
        }

        final int numberOfParts = rbelHttpMessage.getRawContent().length / MAX_MESSAGE_SIZE + 1;
        for (int i = 0; i < numberOfParts; i++) {
            byte[] partContent = Arrays.copyOfRange(
                rbelHttpMessage.getRawContent(),
                i * MAX_MESSAGE_SIZE,
                Math.min((i + 1) * MAX_MESSAGE_SIZE, rbelHttpMessage.getRawContent().length)
            );

            log.trace("Sending part {} of {} for UUID {}...", i, numberOfParts, rbelHttpMessage.getUuid());
            template.convertAndSend(TigerRemoteProxyClient.WS_DATA,
                TracingMessagePart.builder()
                    .data(partContent)
                    .index(i)
                    .uuid(rbelHttpMessage.getUuid())
                    .numberOfMessages(numberOfParts)
                    .build());
        }
    }
}
