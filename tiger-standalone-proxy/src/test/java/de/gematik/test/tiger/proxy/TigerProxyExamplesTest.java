package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.modifier.RbelModificationDescription;
import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.config.tigerProxy.TigerTlsConfiguration;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@ExtendWith(MockServerExtension.class)
@RequiredArgsConstructor
public class TigerProxyExamplesTest {

    private final ClientAndServer mockServerClient;

    @BeforeEach
    public void beforeEachLifecyleMethod() {
        mockServerClient.when(request()
                .withPath("/foo"))
            .respond(httpRequest ->
                response()
                    .withBody("bar" + httpRequest.getFirstQueryStringParameter("echo")));

        mockServerClient.when(request()
                .withPath("/read"))
            .respond(httpRequest ->
                response().withBody(FileUtils.readFileToByteArray(new File(
                    httpRequest.getFirstQueryStringParameter("filename")
                ))));
    }

    @Test
    public void directTest() {
        final HttpResponse<String> response = Unirest.get("http://localhost:" + mockServerClient.getPort() + "/foo")
            .asString();

        assertThat(response.getBody())
            .isEqualTo("bar");
    }

    @Test
    public void simpleTigerProxyTest() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .build());
        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().proxy("localhost", tigerProxy.getPort());
        unirestInstance.get("http://localhost:" + mockServerClient.getPort() + "/foo?echo=schmoolildu").asString();

        assertThat(tigerProxy.getRbelMessages().get(1).getRawStringContent())
            .contains("barschmoolildu");
    }

    @Test
    public void rbelPath_getBody() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .build());
        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().proxy("localhost", tigerProxy.getPort());
        unirestInstance.get("http://localhost:" + mockServerClient.getPort() + "/foo?echo=schmoolildu").asString();

        assertThat(tigerProxy.getRbelMessages().get(1).findElement("$.body")
            .get().getRawStringContent())
            .isEqualTo("barschmoolildu");
    }

    @Test
    public void json_demoWithExtendedRbelPath() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .build());

        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().proxy("localhost", tigerProxy.getPort());
        unirestInstance.get("http://localhost:" + mockServerClient.getPort() + "/read?filename=src/test/resources/test.json").asString();

        assertThat(tigerProxy.getRbelMessages().get(1).findElement("$.body.webdriver.*.driver")
            .get().getRawStringContent())
            .contains("targetValue");
    }

    @Test
    public void jsonInXml_longerRbelPathFailing() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .build());
        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().proxy("localhost", tigerProxy.getPort());
        unirestInstance.get("http://localhost:" + mockServerClient.getPort() + "/read?filename=src/test/resources/combined.json").asString();

        RbelOptions.activateRbelPathDebugging();
        tigerProxy.getRbelMessages().get(1).findElement("$.body.xmlContent.RegistryResponse.RegistryErrorList.*.webdriver");
        RbelOptions.deactivateRbelPathDebugging();
    }

    @Test
    public void jsonInXml_longerRbelPathSucceeding() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .build());
        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().proxy("localhost", tigerProxy.getPort());
        unirestInstance.get("http://localhost:" + mockServerClient.getPort() + "/read?filename=src/test/resources/combined.json").asString();

        RbelOptions.activateRbelPathDebugging();
        assertThat(tigerProxy.getRbelMessages().get(1).findElement("$..textTest.hier")
            .get().getRawStringContent())
            .isEqualTo("ist kein text");
    }

    @Test
    public void forwardProxyRoute_sendMessage() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://no.real.server")
                .to("http://localhost:" + mockServerClient.getPort())
                .build()))
            .build());
        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().proxy("localhost", tigerProxy.getPort());
        unirestInstance.get("http://no.real.server/foo").asString();

        assertThat(tigerProxy.getRbelMessages().get(1).findElement("$.body")
            .get().getRawStringContent())
            .isEqualTo("bar");
    }

    @Test
    public void forwardProxyRoute_waitForMessageSent() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://no.real.server")
                .to("http://localhost:" + mockServerClient.getPort())
                .build()))
            .build());

        System.out.println("curl -v http://no.real.server/foo -x localhost:" + tigerProxy.getPort());
        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().proxy("localhost", tigerProxy.getPort());
        unirestInstance.get("http://no.real.server/foo").asString();

        await().atMost(2, TimeUnit.SECONDS)
            .until(() -> tigerProxy.getRbelMessages().size() >= 2);
    }

    @Test
    public void reverseProxyRoute_waitForMessageSent() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + mockServerClient.getPort())
                .build()))
            .build());

        System.out.println("curl -v http://localhost:" + tigerProxy.getPort() + "/foo");
        Unirest.get("http://localhost:" + tigerProxy.getPort() + "/foo").asString();

        await().atMost(2, TimeUnit.SECONDS)
            .until(() -> tigerProxy.getRbelMessages().size() >= 2);
    }

    @Test
    public void reverseProxyDeepRoute_waitForMessageSent() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/wuff")
                .to("http://localhost:" + mockServerClient.getPort())
                .build()))
            .build());

        System.out.println("curl -v http://localhost:" + tigerProxy.getPort() + "/wuff/foo");
        Unirest.get("http://localhost:" + tigerProxy.getPort() + "/wuff/foo").asString();

        await().atMost(2, TimeUnit.SECONDS)
            .until(() -> tigerProxy.getRbelMessages().size() >= 2);
    }

    @Test
    public void reverseProxyWithTls_waitForMessageSent() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + mockServerClient.getPort())
                .build()))
            .build());

        System.out.println("curl -v https://localhost:" + tigerProxy.getPort() + "/foo");
        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().sslContext(tigerProxy.buildSslContext());
        unirestInstance.get("https://localhost:" + tigerProxy.getPort() + "/foo").asString();

        await().atMost(2, TimeUnit.SECONDS)
            .until(() -> tigerProxy.getRbelMessages().size() >= 2);
    }

    @Test
    public void forwardProxyWithTls_waitForMessageSent() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://blub")
                .to("http://localhost:" + mockServerClient.getPort())
                .build()))
            .tls(TigerTlsConfiguration.builder()
                .domainName("blub")
                .build())
            .build());

        System.out.println("curl -v https://blub/foo -x http://localhost:" + tigerProxy.getPort() + " -k");
        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().proxy("localhost", tigerProxy.getPort());
        unirestInstance.config().verifySsl(false);
        unirestInstance.get("https://blub/foo").asString();

        await().atMost(2, TimeUnit.SECONDS)
            .until(() -> tigerProxy.getRbelMessages().size() >= 2);
    }

    @Test
    @Disabled("Doesnt work on some JVMs (Brainpool restrictions)")
    public void forwardProxyWithTlsAndCustomCa_waitForMessageSent() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://blub")
                .to("http://localhost:" + mockServerClient.getPort())
                .build()))
            .tls(TigerTlsConfiguration.builder()
                .serverRootCa(new TigerPkiIdentity("../tiger-proxy/src/test/resources/customCa.p12;00"))
                .build())
            .build());

        System.out.println("curl -v https://blub/foo -x http://localhost:" + tigerProxy.getPort() + " -k");
        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().proxy("localhost", tigerProxy.getPort());
        unirestInstance.config().verifySsl(false);
        unirestInstance.get("https://blub/foo").asString();

        await().atMost(2, TimeUnit.SECONDS)
            .until(() -> tigerProxy.getRbelMessages().size() >= 2);
    }

    @Test
    @Disabled
    public void twoProxiesWithTrafficForwarding_shouldShowTraffic() {
        // standalone-application starten!
        // webui öffnen

        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .trafficEndpoints(List.of("http://localhost:8080"))
            .build());
        System.out.println("curl -v https://api.twitter.com/1.1/jot/client_event.json -x http://localhost:6666 -k");

        await().atMost(2, TimeUnit.HOURS)
            .until(() -> tigerProxy.getRbelMessages().size() >= 4);
    }

    @Test
    public void modificationForReturnValue() {
        RbelOptions.activateJexlDebugging();
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://blub")
                .to("http://localhost:" + mockServerClient.getPort())
                .build()))
            .modifications(List.of(RbelModificationDescription.builder()
                .targetElement("$.body")
                .replaceWith("horridoh!")
                .build()))
            .build());
        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().proxy("localhost", tigerProxy.getPort());
        unirestInstance.get("http://blub/foo").asString();

        assertThat(tigerProxy.getRbelMessages().get(1).findElement("$.body")
            .get().getRawStringContent())
            .isEqualTo("horridoh!");
    }
}
