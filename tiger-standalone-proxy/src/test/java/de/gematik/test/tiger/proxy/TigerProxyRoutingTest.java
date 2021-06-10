package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;

import de.gematik.test.tiger.proxy.controller.TigerConfigurationController;
import kong.unirest.Unirest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ExtendWith(MockServerExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TigerProxyRoutingTest {

    private final ClientAndServer mockServerClient;
    @Autowired
    private TigerProxy tigerProxy;
    @Autowired
    private TigerConfigurationController tigerConfigurationController;

    public TigerProxyRoutingTest(ClientAndServer mockServerClient) {
        this.mockServerClient = mockServerClient;
    }

    @BeforeEach
    public void beforeEachLifecyleMethod() {
        tigerProxy.addRoute("http://myserv.er", "http://localhost:" + mockServerClient.getPort());

        mockServerClient.when(request()
            .withPath("/foo"))
            .respond(httpRequest ->
                HttpResponse.response()
                    .withBody("bar"));

        Unirest.config().reset();
        Unirest.config().proxy("localhost", tigerProxy.getPort());
    }

    @Test
    public void shouldHonorConfiguredRoutes() {
        assertThat(Unirest.get("http://myserv.er/foo").asString().getBody())
            .isEqualTo("bar");
    }

//    @Test
    //TODO next here
    public void addRoute_shouldWork() {
        Unirest.put("http://tiger.proxy/configuration/route")
            .body("{\"from\":\"http://anderer.server\","
                + "\"to\":\"http://localhost:" + mockServerClient.getPort()+"\"}")
            .asEmpty();

        assertThat(Unirest.get("http://anderer.server/foo").asString().getBody())
            .isEqualTo("bar");
    }
}
