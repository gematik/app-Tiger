package de.gematik.test.tiger.proxy.client;

import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.data.TigerRoute;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyRouteConflictException;
import kong.unirest.Unirest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@ExtendWith(MockServerExtension.class)
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RequiredArgsConstructor
@Slf4j
public class TigerRemoteProxyClientTest {

    // the remote server (to which we send requests)
    private final ClientAndServer mockServerClient;
    // the remote proxy (routing the requests to the remote server)
    @Autowired
    private TigerProxy tigerProxy;
    // the local TigerProxy-Client (which syphons the message from the remote tiger-proxy)
    private TigerRemoteProxyClient tigerRemoteProxyClient;

    @LocalServerPort
    private int springServerPort;

    @BeforeEach
    public void setup() {
        if (tigerRemoteProxyClient == null) {
            tigerRemoteProxyClient = new TigerRemoteProxyClient("http://localhost:" + springServerPort,
                    new TigerProxyConfiguration());
        }

        try {
            tigerProxy.addRoute(TigerRoute.builder()
                    .from("http://myserv.er")
                    .to("http://localhost:" + mockServerClient.getPort())
                    .build());
        } catch (TigerProxyRouteConflictException e) {
            tigerProxy.removeRoute(e.getExistingRoute().getId());
            tigerProxy.addRoute(TigerRoute.builder()
                    .from("http://myserv.er")
                    .to("http://localhost:" + mockServerClient.getPort())
                    .build());
        }

        mockServerClient.when(request().withPath("/foo"))
                .respond(httpRequest -> response().withBody("bar"));

        mockServerClient.when(request().withPath("/echo"))
                .respond(httpRequest -> response()
                        .withHeaders(httpRequest.getHeaders())
                        .withBody(httpRequest.getBodyAsRawBytes()));

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());
    }

    @Test
    public void sendMessage_shouldTriggerListener() {
        log.info("Send Routes: {}", tigerProxy.getRoutes());
        log.info("wanted tomcat port: {}", mockServerClient.getPort());

        AtomicInteger listenerCallCounter = new AtomicInteger(0);
        tigerRemoteProxyClient.addRbelMessageListener(message -> listenerCallCounter.incrementAndGet());

        Unirest.get("http://myserv.er/foo").asString()
                .ifFailure(response -> fail(""));

        await()
                .atMost(2, TimeUnit.SECONDS)
                .until(() -> listenerCallCounter.get() > 0);
    }

    @Test
    public void rawBytesInMessage_shouldSurviveReconstruction() {
        log.info("Bytes Routes: {}", tigerProxy.getRoutes());
        log.info("wanted tomcat port: {}", mockServerClient.getPort());

        Unirest.post("http://myserv.er/echo")
                .body(DigestUtils.sha256("hello"))
                .asString()
                .ifFailure(response -> fail(""));

        await()
                .atMost(2, TimeUnit.SECONDS)
                .until(() -> !tigerRemoteProxyClient.getRbelLogger().getMessageHistory().isEmpty());

        assertThat(tigerRemoteProxyClient.getRbelMessages().get(0).getHttpMessage().getRawBody())
                .isEqualTo(DigestUtils.sha256("hello"));
    }

    public void testUI() throws InterruptedException {
        Thread.sleep(200000000);
    }
}