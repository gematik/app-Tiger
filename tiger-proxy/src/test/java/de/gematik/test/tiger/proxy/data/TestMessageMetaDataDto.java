/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */
package de.gematik.test.tiger.proxy.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.AbstractTigerProxyTest;
import java.util.List;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.SocketAddress;
import org.mockserver.netty.MockServer;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
public class TestMessageMetaDataDto extends AbstractTigerProxyTest {

    public static MockServerClient forwardProxy;

    @BeforeAll
    public static void setupForwardProxy() {
        final MockServer forwardProxyServer = new MockServer();

        forwardProxy = new MockServerClient("localhost", forwardProxyServer.getLocalPort());
        log.info("Started Forward-Proxy-Server on port {}", forwardProxy.getPort());

        forwardProxy.when(request())
            .forward(
                req -> forwardOverriddenRequest(
                    req.withSocketAddress(
                        "localhost", fakeBackendServer.port(), SocketAddress.Scheme.HTTP
                    ))
                    .getRequestOverride());
    }

    @AfterAll
    public static void tearDownMockServer() throws ExecutionException, InterruptedException {
        forwardProxy.stopAsync().get();
    }

    @Test
    void checkMessageMetaDataDtoConversion()  {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        proxyRest.get("http://backend/foobar").asJson();
        awaitMessagesInTiger(2);

        MessageMetaDataDto message0 = MessageMetaDataDto.createFrom(tigerProxy.getRbelMessagesList().get(0));
        assertThat(message0.getPath()).isEqualTo("/foobar");
        assertThat(message0.getMethod()).isEqualTo("GET");
        assertThat(message0.getResponseCode()).isNull();
        assertThat(message0.getRecipient()).isEqualTo("backend:80");
        //TODO TGR-651 wieder reaktivieren
        // assertThat(message0.getSender()).matches("(view-|)localhost:\\d*");
        assertThat(message0.getSequenceNumber()).isEqualTo(0);

        MessageMetaDataDto message1 = MessageMetaDataDto.createFrom(tigerProxy.getRbelMessagesList().get(1));
        assertThat(message1.getPath()).isNull();
        assertThat(message1.getMethod()).isNull();
        assertThat(message1.getResponseCode()).isEqualTo(666);
        //TODO TGR-651 wieder reaktivieren
        // assertThat(message1.getRecipient()).matches("(view-|)localhost:\\d*");
        assertThat(message1.getSender()).isEqualTo("backend:80");
        assertThat(message1.getSequenceNumber()).isEqualTo(1);
    }
}
