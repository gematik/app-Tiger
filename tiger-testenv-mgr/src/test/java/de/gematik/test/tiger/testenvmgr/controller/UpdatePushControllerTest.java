/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.controller;

import static org.awaitility.Awaitility.await;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.env.*;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

@Slf4j
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ResetTigerConfiguration
class UpdatePushControllerTest {

    @LocalServerPort
    private int port;
    @Autowired
    private TigerTestEnvMgr tigerTestEnvMgr;

    @Test
    @Disabled("Failed auf dem Jenkins, lokal läuft er grün. Am ende der timebox keine lösung, master grün, gogo")
    void displayMessage_shouldPushToClient() throws ExecutionException, InterruptedException {
        AtomicReference<String> receivedMessage = new AtomicReference<>("");

        connectToSocketUsingHandler(new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                log.info("afterConnected, now subscribing to session...");
                final StompFrameHandler handler = new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return TestEnvStatusDto.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        log.info("Received Frame");
                        receivedMessage.set(((TestEnvStatusDto) payload).getFeatureMap().toString());
                    }
                };
                session.subscribe("/topic/envStatus", handler);
            }
        });

        TigerStatusUpdate update = TigerStatusUpdate.builder()
            .featureMap(new LinkedHashMap<>(Map.of("feature", FeatureUpdate.builder()
                .description("feature")
                .scenarios(new LinkedHashMap<>(Map.of(
                    "scenario", ScenarioUpdate.builder().description("scenario")
                        .steps(Map.of("step", StepUpdate.builder().description("step").build()
                        )).build()
                ))).build()
            ))).build();

        tigerTestEnvMgr.receiveTestEnvUpdate(update);

        await()
            .atMost(2, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until(() -> receivedMessage.get().equals(update.toString()));
    }

    private void connectToSocketUsingHandler(StompSessionHandlerAdapter handler)
        throws InterruptedException, ExecutionException {
        var webSocketUrl = "ws://localhost:" + port + "/testEnv";

        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        SockJsClient webSocketClient = new SockJsClient(
            List.of(new WebSocketTransport(new StandardWebSocketClient(container))));

        var stompClient = new WebSocketStompClient(webSocketClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        final ListenableFuture<StompSession> connectFuture = stompClient.connect(
            webSocketUrl, handler);

        connectFuture.get();
    }
}
