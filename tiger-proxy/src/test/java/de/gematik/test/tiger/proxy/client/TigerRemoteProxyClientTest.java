/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyRouteConflictException;
import de.gematik.test.tiger.proxy.tracing.TracingPushController;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kong.unirest.Config;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@WireMockTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {"tigerProxy.activateRbelParsing: false"})
@RequiredArgsConstructor
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
@Slf4j
public class TigerRemoteProxyClientTest {
    /*
     *  Our Testsetup:
     *
     *
     * -------------------     --------------    ---------------------------
     * | unirestInstance |  -> | tigerProxy | -> | remoteServer (Wiremock) |
     * -------------------     ------    ----    ---------------------------
     *          ^                     \ /
     *          ?                      V
     *          ----<-----<-----<--Tracing
     *
     */

    // the remote server (to which we send requests) is WireMock (see @WireMockTest above the class)

    // the remote proxy (routing the requests to the remote server)
    @Autowired
    private TigerProxy tigerProxy;

    // the local TigerProxy-Client (which syphons the message from the remote tiger-proxy)
    private TigerRemoteProxyClient tigerRemoteProxyClient;
    private UnirestInstance unirestInstance;

    @LocalServerPort
    private int springServerPort;

    @BeforeEach
    public void setup(WireMockRuntimeInfo remoteServer) {
        log.info("Setup remote client... {}, {}", tigerRemoteProxyClient, tigerProxy);
        TigerProxyConfiguration cfg = TigerProxyConfiguration.builder()
            .proxyLogLevel("WARN")
            .build();
        tigerRemoteProxyClient = new TigerRemoteProxyClient("http://localhost:" + springServerPort,
            cfg);

        remoteServer.getWireMock().register(get("/foo")
            .willReturn(aResponse()
                .withBody("bar")));
        remoteServer.getWireMock().register(post("/foo")
            .willReturn(aResponse()
                .withBody("bar")));
        remoteServer.getWireMock().register(get("/")
            .willReturn(aResponse()
                .withBody("emptyPath!!!")));

        log.info("Configuring routes...");
        try {
            tigerProxy.addRoute(TigerRoute.builder()
                .from("http://myserv.er")
                .to("http://localhost:" + remoteServer.getHttpPort())
                .build());
        } catch (TigerProxyRouteConflictException e) {
            tigerProxy.removeRoute(e.getExistingRoute().getId());
            tigerProxy.addRoute(TigerRoute.builder()
                .from("http://myserv.er")
                .to("http://localhost:" + remoteServer.getHttpPort())
                .build());
        }

        unirestInstance = new UnirestInstance(
            new Config().proxy("localhost", tigerProxy.getPort()));
    }

    @AfterEach
    public void clearRoutes() {
        log.info("Clearing all routes");
        tigerRemoteProxyClient.unsubscribe();
        tigerRemoteProxyClient.close();
        tigerProxy.clearAllRoutes();
        tigerProxy.getRbelLogger().getMessageHistory().clear();
        log.info("Messages {}", tigerProxy.getRbelMessages().size());
        tigerRemoteProxyClient = null;
        unirestInstance.shutDown();
    }

    @Test
    public void sendMessage_shouldTriggerListener() {
        AtomicInteger listenerCallCounter = new AtomicInteger(0);
        tigerRemoteProxyClient.addRbelMessageListener(message -> listenerCallCounter.incrementAndGet());

        unirestInstance.get("http://myserv.er/foo").asString()
            .ifFailure(response -> fail(""));

        await()
            .atMost(2, TimeUnit.SECONDS)
            .until(() -> listenerCallCounter.get() > 0);

        // assert that only two messages are present
        assertThat(tigerProxy.getRbelMessages())
            .hasSize(2);
        // assert that the messages only have rudimentary information
        // (no parsing did take place on the sending tigerProxy)
        assertThat(tigerProxy.getRbelMessages().get(0).findRbelPathMembers("$..*"))
            .hasSize(4);
        assertThat(tigerProxy.getRbelMessages().get(1).findRbelPathMembers("$..*"))
            .hasSize(4);
    }

    @Test
    public void rawBytesInMessage_shouldSurviveReconstruction(WireMockRuntimeInfo wmRuntimeInfo) {
        wmRuntimeInfo.getWireMock().register(post(urlEqualTo("/binary"))
            .willReturn(aResponse()
                .withBody(DigestUtils.sha256("helloResponse"))));

        unirestInstance.post("http://myserv.er/binary")
            .body(DigestUtils.sha256("helloRequest"))
            .asString()
            .ifFailure(response -> fail(""));

        await()
            .atMost(2, TimeUnit.SECONDS)
            .until(() -> !tigerRemoteProxyClient.getRbelLogger().getMessageHistory().isEmpty());

        assertThat(tigerRemoteProxyClient.getRbelMessages().get(0).findRbelPathMembers("$.body").get(0).getRawContent())
            .isEqualTo(DigestUtils.sha256("helloRequest"));
        assertThat(tigerRemoteProxyClient.getRbelMessages().get(1).findRbelPathMembers("$.body").get(0).getRawContent())
            .isEqualTo(DigestUtils.sha256("helloResponse"));
    }

    @Test
    public void giantMessage_shouldTriggerListener() {
        AtomicInteger listenerCallCounter = new AtomicInteger(0);
        tigerRemoteProxyClient.addRbelMessageListener(message -> listenerCallCounter.incrementAndGet());

        final String body = RandomStringUtils.randomAlphanumeric(TracingPushController.MAX_MESSAGE_SIZE * 2);
        unirestInstance.post("http://myserv.er/foo")
            .body(body)
            .asString()
            .ifFailure(response -> fail(""));

        await()
            .atMost(20, TimeUnit.SECONDS)
            .until(() -> listenerCallCounter.get() > 0);

        assertThat(
            new String(tigerRemoteProxyClient.getRbelMessages().get(tigerRemoteProxyClient.getRbelMessages().size() - 2)
                .findElement("$.body").get().getRawContent()))
            .isEqualTo(body);
    }

    @ParameterizedTest
    @CsvSource({
        "/foo, /foo/bar",
        "/foo/bar, /foo",
        "/foo/robots.txt, /foo",
        "/foo, /foo",
        "/foo, /foo/robots.txt",
        "http://foo/bar, http://foo/",
        "http://foo/, http://foo/bar",
        "http://foo/, http://foo",
        "http://foo, http://foo/",
        "http://foo/, http://foo/",
        "https://foo, http://foo"
    })
    public void addTwoCompetingRoutes_secondOneShouldFail(String firstRoute, String secondRoute,
        WireMockRuntimeInfo remoteServer) {
        tigerRemoteProxyClient.addRoute(TigerRoute.builder()
            .from(firstRoute)
            .to("http://localhost:" + remoteServer.getHttpPort())
            .build());

        assertThatThrownBy(() -> tigerRemoteProxyClient.addRoute(TigerRoute.builder()
            .from(secondRoute)
            .to("http://localhost:" + remoteServer.getHttpPort())
            .build())).isInstanceOf(RuntimeException.class);
    }

    @Test
    public void addAndDeleteRoute_shouldWork(WireMockRuntimeInfo remoteServer) {
        final String routeId = tigerRemoteProxyClient.addRoute(TigerRoute.builder()
            .from("http://new.server")
            .to("http://localhost:" + remoteServer.getHttpPort())
            .build()).getId();

        assertThat(unirestInstance.post("http://new.server/foo")
            .asString()
            .getBody())
            .isEqualTo("bar");

        tigerRemoteProxyClient.removeRoute(routeId);

        assertThat(unirestInstance.post("http://new.server/foo")
            .asString()
            .getStatus())
            .isEqualTo(404);
    }

    @Test
    public void listRoutes(WireMockRuntimeInfo remoteServer) {
        final List<TigerRoute> routes = tigerRemoteProxyClient.getRoutes();

        assertThat(routes)
            .extracting("from", "to", "disableRbelLogging")
            .contains(tuple("http://myserv.er", "http://localhost:" + remoteServer.getHttpPort(), false),
                tuple("http://tiger.proxy", "http://localhost:" + springServerPort, true));
    }

    @Test
    public void reverseProxyRoute_checkRemoteTransmission(WireMockRuntimeInfo remoteServer) {
        tigerProxy.addRoute(TigerRoute.builder()
            .from("/blub")
            .to("http://localhost:" + remoteServer.getHttpPort())
            .build());

        AtomicInteger listenerCallCounter = new AtomicInteger(0);
        tigerRemoteProxyClient.addRbelMessageListener(message -> listenerCallCounter.incrementAndGet());

        assertThat(Unirest.get("http://localhost:" + tigerProxy.getPort() + "/blub/foo").asString()
            .ifFailure(response -> fail("Failure from server: " + response.getBody()))
            .getBody())
            .isEqualTo("bar");

        await()
            .atMost(2, TimeUnit.SECONDS)
            .until(() -> listenerCallCounter.get() > 0);
    }

    @Test
    public void reverseProxyRootRoute_checkRemoteTransmission(WireMockRuntimeInfo remoteServer) {
        tigerProxy.addRoute(TigerRoute.builder()
            .from("/")
            .to("http://localhost:" + remoteServer.getHttpPort())
            .build());

        AtomicInteger listenerCallCounter = new AtomicInteger(0);
        tigerRemoteProxyClient.addRbelMessageListener(message -> listenerCallCounter.incrementAndGet());

        assertThat(Unirest.get("http://localhost:" + tigerProxy.getPort() + "/foo").asString()
            .ifFailure(response -> fail("Failure from server: " + response.getBody()))
            .getBody())
            .isEqualTo("bar");

        await()
            .atMost(2, TimeUnit.SECONDS)
            .until(() -> listenerCallCounter.get() > 0);
    }

    @Test
    public void requestToBaseUrl_shouldBeForwarded() {
        AtomicInteger listenerCallCounter = new AtomicInteger(0);
        tigerRemoteProxyClient.addRbelMessageListener(message -> listenerCallCounter.incrementAndGet());

        unirestInstance.get("http://myserv.er").asString();

        await()
            .atMost(2, TimeUnit.SECONDS)
            .until(() -> listenerCallCounter.get() > 0);

        assertThat(tigerRemoteProxyClient.getRbelMessages().get(0).findElement("$.path"))
            .get()
            .extracting(RbelElement::getRawStringContent)
            .isEqualTo("/");
    }

    @Test
    public void downstreamTigerProxyWithFilterCriterion_shouldOnlyShowMatchingMessages() {
        var filteredTigerProxy = new TigerRemoteProxyClient("http://localhost:" + springServerPort,
            TigerProxyConfiguration.builder()
                .proxyLogLevel("WARN")
                .trafficEndpointFilterString("request.url =$ 'faa'")
                .build());

        AtomicInteger listenerCallCounter = new AtomicInteger(0);
        filteredTigerProxy.addRbelMessageListener(message -> {
            if (message.hasFacet(RbelHttpRequestFacet.class)
                && message.getFacetOrFail(RbelHttpRequestFacet.class)
                .getPath().getRawStringContent().endsWith("faa")) {
                // this ensures we only leave the wait after the /faa call
                listenerCallCounter.incrementAndGet();
            }
        });

        unirestInstance.get("http://myserv.er/foo").asString();
        unirestInstance.get("http://myserv.er/faa").asString();

        await()
            .atMost(2, TimeUnit.SECONDS)
            .until(() -> listenerCallCounter.get() > 0);

        assertThat(filteredTigerProxy.getRbelMessages())
            .hasSize(2);
    }
}
