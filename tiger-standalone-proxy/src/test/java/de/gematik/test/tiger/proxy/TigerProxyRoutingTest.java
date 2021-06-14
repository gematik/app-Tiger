package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import de.gematik.test.tiger.proxy.data.TigerRouteDto;
import java.util.List;
import kong.unirest.GenericType;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ExtendWith(MockServerExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RequiredArgsConstructor
public class TigerProxyRoutingTest {

    private final ClientAndServer mockServerClient;
    @Autowired
    private TigerProxy tigerProxy;

    @BeforeEach
    public void beforeEachLifecyleMethod() {
        tigerProxy.getRoutes()
            .stream()
            .filter(route -> !route.getFrom().contains("tiger"))
            .forEach(tigerRoute -> tigerProxy.removeRoute(tigerRoute.getId()));

        tigerProxy.addRoute("http://myserv.er", "http://localhost:" + mockServerClient.getPort());

        mockServerClient.when(request()
            .withPath("/foo"))
            .respond(httpRequest ->
                response()
                    .withBody("bar"));

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());
    }

    @Test
    public void shouldHonorConfiguredRoutes() {
        assertThat(Unirest.get("http://myserv.er/foo").asString().getBody())
            .isEqualTo("bar");
    }

    @Test
    public void addRoute_shouldWork() {
        Unirest.put("http://tiger.proxy/route")
            .header("Content-Type", "application/json")
            .body("{\"from\":\"http://anderer.server\","
                + "\"to\":\"http://localhost:" + mockServerClient.getPort() + "\"}")
            .asEmpty()
            .ifFailure(response -> fail("Cant reach tiger proxy"));

        assertThat(Unirest.get("http://anderer.server/foo").asString().getBody())
            .isEqualTo("bar");
    }

    @Test
    public void deleteRoute_shouldWork() {
        assertThat(Unirest.get("http://temp.server/foo").asEmpty().getStatus())
            .isEqualTo(404);

        String routeId = Unirest.put("http://tiger.proxy/route")
            .header("Content-Type", "application/json")
            .body("{\"from\":\"http://temp.server\","
                + "\"to\":\"http://localhost:" + mockServerClient.getPort() + "\"}")
            .asObject(TigerRouteDto.class)
            .getBody().getId();

        assertThat(Unirest.get("http://temp.server/foo").asEmpty().getStatus())
            .isEqualTo(200);

        Unirest.delete("http://tiger.proxy/route/" + routeId)
            .asEmpty()
            .ifFailure(response -> fail("Cant reach tiger proxy"));

        assertThat(Unirest.get("http://temp.server/foo").asEmpty().getStatus())
            .isEqualTo(404);
    }

    @Test
    public void getRoutes_shouldGiveAllRoutes() {
        final HttpResponse<List<TigerRouteDto>> tigerRoutesResponse = Unirest.get("http://tiger.proxy/route")
            .asObject(new GenericType<>() {
            });

        assertThat(tigerRoutesResponse.getStatus()).isEqualTo(200);
        var tigerRoutes = tigerRoutesResponse.getBody();
        assertThat(tigerRoutes)
            .extracting(TigerRouteDto::getFrom)
            .contains("http://tiger.proxy", "http://myserv.er");
    }
}
