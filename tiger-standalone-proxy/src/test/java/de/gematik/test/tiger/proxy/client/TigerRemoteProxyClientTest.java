/*
 * Copyright (c) 2021 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.proxy.client;

import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyRouteConflictException;
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
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@ExtendWith(MockServerExtension.class)
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RequiredArgsConstructor
@Slf4j
@DirtiesContext
public class TigerRemoteProxyClientTest {
    /*
     *  Our Testsetup:
     *
     *
     * -------------------     --------------    --------------------
     * | unirestInstance |  -> | tigerProxy | -> | mockServerClient |
     * -------------------     ------    ----    --------------------
     *          ^                     \ /
     *          ?                      V
     *          ----<-----<-----<--Tracing
     *
     */

    // the remote server (to which we send requests)
    private final ClientAndServer mockServerClient;

    // the remote proxy (routing the requests to the remote server)
    @Autowired
    private TigerProxy tigerProxy;

    // the local TigerProxy-Client (which syphons the message from the remote tiger-proxy)
    private TigerRemoteProxyClient tigerRemoteProxyClient;
    private UnirestInstance unirestInstance;

    @LocalServerPort
    private int springServerPort;

    @BeforeEach
    public void setup() {
        if (tigerRemoteProxyClient == null) {
            tigerRemoteProxyClient = new TigerRemoteProxyClient("http://localhost:" + springServerPort,
                TigerProxyConfiguration.builder()
                    .proxyLogLevel("WARN")
                    .build());
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

        unirestInstance = new UnirestInstance(
            new Config().proxy("localhost", tigerProxy.getPort()));
    }

    @AfterEach
    public void clearRoutes() {
        tigerProxy.clearAllRoutes();
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
    }

    @Test
    public void rawBytesInMessage_shouldSurviveReconstruction() {
        unirestInstance.post("http://myserv.er/echo")
            .body(DigestUtils.sha256("hello"))
            .asString()
            .ifFailure(response -> fail(""));

        await()
            .atMost(2, TimeUnit.SECONDS)
            .until(() -> !tigerRemoteProxyClient.getRbelLogger().getMessageHistory().isEmpty());

        assertThat(tigerRemoteProxyClient.getRbelMessages().get(0).findRbelPathMembers("$.body").get(0).getRawContent())
            .isEqualTo(DigestUtils.sha256("hello"));
    }

    @Test
    public void giantMessage_shouldTriggerListener() {
        AtomicInteger listenerCallCounter = new AtomicInteger(0);
        tigerRemoteProxyClient.addRbelMessageListener(message -> listenerCallCounter.incrementAndGet());

        unirestInstance.post("http://myserv.er/foo")
            .body(RandomStringUtils.randomAlphanumeric(1024 * 10))
            .asString()
            .ifFailure(response -> fail(""));

        await()
            .atMost(2, TimeUnit.SECONDS)
            .until(() -> listenerCallCounter.get() > 0);
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
  public void addTwoCompetingRoutes_secondOneShouldFail(String firstRoute, String secondRoute) {
        tigerRemoteProxyClient.addRoute(TigerRoute.builder()
            .from(firstRoute)
            .to("http://localhost:" + mockServerClient.getPort())
            .build()).getId();

        assertThatThrownBy(() -> tigerRemoteProxyClient.addRoute(TigerRoute.builder()
            .from(secondRoute)
            .to("http://localhost:" + mockServerClient.getPort())
            .build()).getId()).isInstanceOf(RuntimeException.class);
    }

    @Test
    public void addAndDeleteRoute_shouldWork() {
        final String routeId = tigerRemoteProxyClient.addRoute(TigerRoute.builder()
            .from("http://new.server")
            .to("http://localhost:" + mockServerClient.getPort())
            .build()).getId();

        assertThat(unirestInstance.post("http://new.server/echo")
            .body("hello new server")
            .asString()
            .getBody())
            .isEqualTo("hello new server");

        tigerRemoteProxyClient.removeRoute(routeId);

        assertThat(unirestInstance.post("http://new.server/echo")
            .body("hello new server")
            .asString()
            .getStatus())
            .isEqualTo(404);
    }

    @Test
    public void listRoutes() {
        final List<TigerRoute> routes = tigerRemoteProxyClient.getRoutes();

        assertThat(routes)
            .extracting("from", "to", "disableRbelLogging")
            .contains(tuple("http://myserv.er", "http://localhost:" + mockServerClient.getPort(), false),
                tuple("http://tiger.proxy", "http://localhost:" + springServerPort, true));
    }

    @Test
    public void reverseProxyRoute_checkRemoteTransmission() {
        tigerProxy.addRoute(TigerRoute.builder()
            .from("/blub")
            .to("http://localhost:" + mockServerClient.getPort())
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
    public void reverseProxyRootRoute_checkRemoteTransmission() {
        tigerProxy.addRoute(TigerRoute.builder()
            .from("/")
            .to("http://localhost:" + mockServerClient.getPort())
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
}
