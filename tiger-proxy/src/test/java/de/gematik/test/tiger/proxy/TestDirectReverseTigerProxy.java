/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.test.tiger.common.data.config.tigerProxy.DirectReverseProxyInfo;
import de.gematik.test.tiger.common.data.config.tigerProxy.ForwardProxyInfo;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.ZonedDateTime;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
class TestDirectReverseTigerProxy extends AbstractTigerProxyTest {

    @Test
    void directForward_shouldForwardBinaryContent() throws IOException {
        try (ServerSocket backendServer = new ServerSocket(0)) {
            spawnTigerProxyWith(TigerProxyConfiguration.builder()
                .directReverseProxy(DirectReverseProxyInfo.builder()
                    .hostname("localhost")
                    .port(backendServer.getLocalPort())
                    .build())
                .build());
            try (Socket clientSocket = new Socket("localhost", tigerProxy.getProxyPort())) {
                final byte[] requestPayload = "Hallo Welt!".getBytes(UTF_8);
                final byte[] responsePayload = "Response String".getBytes(UTF_8);

                ZonedDateTime beforeRequest = ZonedDateTime.now();
                clientSocket.getOutputStream().write(requestPayload);

                final Socket serverSocket = backendServer.accept();
                assertThat(serverSocket.getInputStream().readNBytes(requestPayload.length))
                    .isEqualTo(requestPayload);

                serverSocket.getOutputStream().write(responsePayload);

                assertThat(clientSocket.getInputStream().readNBytes(responsePayload.length))
                    .isEqualTo(responsePayload);
                ZonedDateTime afterRespone = ZonedDateTime.now();

                // check content
                assertThat(tigerProxy.getRbelMessagesList().get(0).getRawContent())
                    .isEqualTo(requestPayload);
                assertThat(tigerProxy.getRbelMessagesList().get(1).getRawContent())
                    .isEqualTo(responsePayload);

                // check request adresses
                assertThat(tigerProxy.getRbelMessagesList().get(0).findElement("$.receiver.port")
                    .get().getRawStringContent())
                    .isEqualTo("" + serverSocket.getLocalPort());
                assertThat(tigerProxy.getRbelMessagesList().get(0).findElement("$.sender.port")
                    .get().getRawStringContent())
                    .isEqualTo("" + clientSocket.getLocalPort());

                // check response adresses
                assertThat(tigerProxy.getRbelMessagesList().get(1).findElement("$.sender.port")
                    .get().getRawStringContent())
                    .isEqualTo("" + serverSocket.getLocalPort());
                assertThat(tigerProxy.getRbelMessagesList().get(1).findElement("$.receiver.port")
                    .get().getRawStringContent())
                    .isEqualTo("" + clientSocket.getLocalPort());

                System.out.println(tigerProxy.getRbelMessagesList().get(0).printTreeStructure());
                // check timing
                final ZonedDateTime requestTime = tigerProxy.getRbelMessagesList().get(0)
                    .getFacetOrFail(RbelMessageTimingFacet.class)
                    .getTransmissionTime();
                final ZonedDateTime responseTime = tigerProxy.getRbelMessagesList().get(0)
                    .getFacetOrFail(RbelMessageTimingFacet.class)
                    .getTransmissionTime();

                assertThat(requestTime)
                    .isAfterOrEqualTo(beforeRequest)
                    .isBeforeOrEqualTo(responseTime)
                    .isBeforeOrEqualTo(afterRespone);

                assertThat(responseTime)
                    .isAfter(beforeRequest)
                    .isAfterOrEqualTo(responseTime)
                    .isBeforeOrEqualTo(afterRespone);
            }
        }
    }

    @Test
    void directForward_shouldForwardHttpContent() {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .directReverseProxy(DirectReverseProxyInfo.builder()
                .hostname("localhost")
                .port(fakeBackendServer.port())
                .build())
            .build());

        // no proxyRest, direct connection (assume reverseProxy behavior)
        Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foobar")
            .asString();
        awaitMessagesInTiger(2);

        assertThat(tigerProxy.getRbelMessagesList().get(1).findElement("$.responseCode")
            .get().getRawStringContent())
            .isEqualTo("666");
    }

    @Test
    void directForwardWithForwardProxy_shouldGiveError() {
        final TigerProxyConfiguration proxyConfiguration = TigerProxyConfiguration.builder()
            .directReverseProxy(DirectReverseProxyInfo.builder()
                .hostname("localhost")
                .port(fakeBackendServer.port())
                .build())
            .forwardToProxy(ForwardProxyInfo.builder()
                .hostname("localhost")
                .port(1234)
                .build())
            .build();

        assertThatThrownBy(() -> spawnTigerProxyWith(proxyConfiguration))
            .isInstanceOf(RuntimeException.class);
    }
}
