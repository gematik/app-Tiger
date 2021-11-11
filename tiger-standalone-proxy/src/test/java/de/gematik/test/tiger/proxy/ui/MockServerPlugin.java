package de.gematik.test.tiger.proxy.ui;

import io.cucumber.plugin.EventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestRunStarted;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.MockServer;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@Slf4j
public class MockServerPlugin implements EventListener {

    private static MockServer mockServer;
    private static MockServerClient mockServerClient;

    public static void startWebServer() {
        mockServer = new MockServer();
        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort());

        setupStubs();
    }

    private static void setupStubs() {
        mockServerClient.when(request()
                .withPath("/test1.html"))
            .respond(httpRequest -> response()
                .withBody("<html><body>test1body</body></html>\n"));
        mockServerClient.when(request()
                .withPath("/test2.html"))
            .respond(httpRequest -> response()
                .withBody("<html><body>test2body</body></html>\n"));
        mockServerClient.when(request()
                .withPath("/test3.html"))
            .respond(httpRequest -> response()
                .withBody("<html><body>test3body</body></html>\n"));
    }

    public static void stopWebServer() {
        if (mockServer != null) {
            mockServer.stop();
            mockServerClient.stop();
        }
    }

    public static String getMockServerPort() {
        if (mockServer == null) {
            throw new RuntimeException("Mockserver not started yet!");
        }
        return String.valueOf(mockServer.getLocalPort());
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestRunStarted.class, event -> {
            log.info("Starting dummy HTTP server...");
            startWebServer();
        });

        publisher.registerHandlerFor(TestRunFinished.class, event -> {
            log.info("Starting dummy HTTP server...");
            stopWebServer();
        });
    }
}
