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

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import kong.unirest.Config;
import kong.unirest.UnirestInstance;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@ExtendWith(SpringExtension.class)
@ExtendWith(MockServerExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RequiredArgsConstructor
public class TigerProxyTracingTest {

    private static final Duration DEFAULT_WAIT_TIME = Duration.of(10, ChronoUnit.SECONDS);
    private final ClientAndServer mockServerClient;
    private UnirestInstance unirestInstance;
    @Autowired
    private TigerProxy tigerProxy;
    private TigerRemoteProxyClient receivingTigerProxy;
    @LocalServerPort
    private int sendingTigerProxyServerPort;

    @BeforeEach
    public void beforeEachLifecyleMethod() {
        tigerProxy.getRoutes()
            .stream()
            .filter(route -> !route.getFrom().contains("tiger"))
            .forEach(tigerRoute -> tigerProxy.removeRoute(tigerRoute.getId()));

        tigerProxy.addRoute(TigerRoute.builder()
            .from("http://myserv.er")
            .to("http://localhost:" + mockServerClient.getPort())
            .build());

        mockServerClient.when(request()
                .withPath("/foo"))
            .respond(httpRequest ->
                response()
                    .withBody("bar"));

        unirestInstance = new UnirestInstance(
            new Config().proxy("localhost", tigerProxy.getPort()));
        unirestInstance.get("");

        receivingTigerProxy = new TigerRemoteProxyClient("http://localhost:" + sendingTigerProxyServerPort,
            TigerProxyConfiguration.builder().build());
    }

    @AfterEach
    public void reset() {
        tigerProxy.clearAllRoutes();
        tigerProxy.getRbelLogger().getRbelConverter().getPostConversionListeners().clear();
        receivingTigerProxy.getRbelMessages().clear();
    }

    @Test
    public void sendMessage_shouldBeForwardedToReceivingProxy() {
        assertThat(receivingTigerProxy.getRbelMessages())
            .isEmpty();

        assertThat(unirestInstance.get("http://myserv.er/foo").asString().getBody())
            .isEqualTo("bar");

        await().atMost(DEFAULT_WAIT_TIME)
            .until(() -> !receivingTigerProxy.getRbelMessages().isEmpty());
    }

    @Test
    public void provokeServerSidedException_clientShouldThrowExceptionAsWell() {
        tigerProxy.getRbelLogger().getRbelConverter().addPostConversionListener((el, cv) -> {
            if (el.hasFacet(RbelHttpResponseFacet.class)) {
                throw new RuntimeException("blub");
            }
        });

        unirestInstance.get("http://myserv.er/foo").asString().getBody();

        await().atMost(DEFAULT_WAIT_TIME)
            .until(() -> !receivingTigerProxy.getReceivedRemoteExceptions().isEmpty());
    }
}
