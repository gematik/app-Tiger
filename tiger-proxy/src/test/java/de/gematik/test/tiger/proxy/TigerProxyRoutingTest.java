/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.fail;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.proxy.data.TigerRouteDto;
import java.util.List;
import kong.unirest.Config;
import kong.unirest.GenericType;
import kong.unirest.HttpResponse;
import kong.unirest.UnirestInstance;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@WireMockTest
@RequiredArgsConstructor
public class TigerProxyRoutingTest {

    private UnirestInstance unirestInstance;
    @Autowired
    private TigerProxy tigerProxy;
    private int backendServerPort;

    @BeforeEach
    public void beforeEachLifecyleMethod(WireMockRuntimeInfo wmRuntimeInfo) {
        tigerProxy.getRoutes()
            .stream()
            .filter(route -> !route.getFrom().contains("tiger"))
            .forEach(tigerRoute -> tigerProxy.removeRoute(tigerRoute.getId()));

        tigerProxy.addRoute(TigerRoute.builder()
            .from("http://myserv.er")
            .to("http://localhost:" + wmRuntimeInfo.getHttpPort())
            .build());

        wmRuntimeInfo.getWireMock().register(get("/foo")
            .willReturn(aResponse()
                .withBody("bar")));

        backendServerPort = wmRuntimeInfo.getHttpPort();

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
                + "\"to\":\"http://localhost:" + backendServerPort + "\"}")
            .asEmpty()
            .ifFailure(response -> fail("Cant reach tiger proxy (" + response.toString() + ")"));

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
                + "\"to\":\"http://localhost:" + backendServerPort + "\"}")
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
}
