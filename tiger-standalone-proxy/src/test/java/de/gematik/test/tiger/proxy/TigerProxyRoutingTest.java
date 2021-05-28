package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;

import kong.unirest.Unirest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
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

    public TigerProxyRoutingTest(ClientAndServer mockServerClient) {
        this.mockServerClient = mockServerClient;
    }

    @Autowired
    private TigerProxy tigerProxy;

    @BeforeEach
    public void beforeEachLifecyleMethod() {
        tigerProxy.addRoute("http://myserv.er", "http://localhost:" + mockServerClient.getPort());

        mockServerClient.when(request()
            .withPath("/foo"))
            .respond(httpRequest ->
                HttpResponse.response()
                    .withBody("bar"));
    }

    @Test
    public void shouldHonorConfiguredRoutes() {
        Unirest.config().proxy("localhost", tigerProxy.getPort());
        assertThat(Unirest.get("http://myserv.er/foo").asString().getBody())
            .isEqualTo("bar");
    }
}
