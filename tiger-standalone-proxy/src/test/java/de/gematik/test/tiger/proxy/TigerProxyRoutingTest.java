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

import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.proxy.data.TigerRouteDto;
import kong.unirest.*;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@ExtendWith(SpringExtension.class)
@ExtendWith(MockServerExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RequiredArgsConstructor
public class TigerProxyRoutingTest {

    private final ClientAndServer mockServerClient;
    private UnirestInstance unirestInstance;
    @Autowired
    private TigerProxy tigerProxy;

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
    }

    @AfterEach
    public void reset() {
        tigerProxy.clearAllRoutes();
    }

    @Test
    public void shouldHonorConfiguredRoutes() {
        assertThat(unirestInstance.get("http://myserv.er/foo").asString().getBody())
                .isEqualTo("bar");
    }

    @Test
    public void addRoute_shouldWork() {
        unirestInstance.put("http://tiger.proxy/route")
                .header("Content-Type", "application/json")
                .body("{\"from\":\"http://anderer.server\","
                        + "\"to\":\"http://localhost:" + mockServerClient.getPort() + "\"}")
                .asEmpty()
                .ifFailure(response -> fail("Cant reach tiger proxy"));

        assertThat(unirestInstance.get("http://anderer.server/foo").asString().getBody())
                .isEqualTo("bar");
    }

    @Test
    public void deleteRoute_shouldWork() {
        assertThat(unirestInstance.get("http://temp.server/foo").asEmpty().getStatus())
                .isEqualTo(404);

        String routeId = unirestInstance.put("http://tiger.proxy/route")
                .header("Content-Type", "application/json")
                .body("{\"from\":\"http://temp.server\","
                        + "\"to\":\"http://localhost:" + mockServerClient.getPort() + "\"}")
                .asObject(TigerRouteDto.class)
                .getBody().getId();

        assertThat(unirestInstance.get("http://temp.server/foo").asEmpty().getStatus())
                .isEqualTo(200);

        unirestInstance.delete("http://tiger.proxy/route/" + routeId)
                .asEmpty()
                .ifFailure(response -> fail("Cant reach tiger proxy"));

        assertThat(unirestInstance.get("http://temp.server/foo").asEmpty().getStatus())
                .isEqualTo(404);
    }

    @Test
    public void getRoutes_shouldGiveAllRoutes() {
        final HttpResponse<List<TigerRouteDto>> tigerRoutesResponse = unirestInstance.get("http://tiger.proxy/route")
                .asObject(new GenericType<>() {
                });

        assertThat(tigerRoutesResponse.getStatus()).isEqualTo(200);
        var tigerRoutes = tigerRoutesResponse.getBody();
        assertThat(tigerRoutes)
                .extracting(TigerRouteDto::getFrom)
                .contains("http://tiger.proxy", "http://myserv.er");
    }

    //@Test
    public void testRouteUI() throws InterruptedException {
        mockServerClient.when(request()
            .withPath("/foo1"))
            .respond(httpRequest ->
                response()
                    .withBody("bar1"));

        String routeId = unirestInstance.put("http://tiger.proxy/route")
            .header("Content-Type", "application/json")
            .body("{\"from\":\"http://temp.server\","
                + "\"to\":\"http://localhost:" + mockServerClient.getPort() + "\"}")
            .asObject(TigerRouteDto.class)
            .getBody().getId();

        Thread.sleep(20000000000L);
    }
}
