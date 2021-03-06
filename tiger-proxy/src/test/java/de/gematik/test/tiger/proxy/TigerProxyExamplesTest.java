/*
 * Copyright (c) 2022 gematik GmbH
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import de.gematik.rbellogger.RbelOptions;
import de.gematik.rbellogger.modifier.RbelModificationDescription;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerTlsConfiguration;
import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity;
import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.*;
import org.mockserver.integration.ClientAndServer;

@RequiredArgsConstructor
public class TigerProxyExamplesTest {

    private static ClientAndServer mockServerClient = new ClientAndServer();

    @BeforeAll
    public static void beforeEachLifecyleMethod() {
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

    @AfterAll
    public static void stopMockServer() {
        mockServerClient.stop();
    }

    @Test
    public void directTest() {
        final HttpResponse<String> response = Unirest.get("http://localhost:" + mockServerClient.getPort() + "/foo")
            .asString();

        assertThat(response.getBody())
            .isEqualTo("bar");
    }

    @Test
    public void simpleTigerProxyTest() throws Exception {
        try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .build())) {
            final UnirestInstance unirestInstance = Unirest.spawnInstance();
            unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
            unirestInstance.get("http://localhost:" + mockServerClient.getPort() + "/foo?echo=schmoolildu").asString();

            assertThat(tigerProxy.getRbelMessages().get(1).getRawStringContent())
                .contains("barschmoolildu");
        }
    }

    @Test
    public void rbelPath_getBody() throws Exception {
        try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder().build());
            UnirestInstance unirestInstance = Unirest.spawnInstance()) {
            unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
            unirestInstance.get("http://localhost:" + mockServerClient.getPort() + "/foo?echo=schmoolildu")
                .asString();

            assertThat(tigerProxy.getRbelMessages().get(1).findElement("$.body")
                .get().getRawStringContent())
                .isEqualTo("barschmoolildu");
        }
    }

    @Test
    public void json_demoWithExtendedRbelPath() throws Exception {
        try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder().build());
            UnirestInstance unirestInstance = Unirest.spawnInstance()) {
            unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
            unirestInstance.get(
                    "http://localhost:" + mockServerClient.getPort() + "/read?filename=src/test/resources/test.json")
                .asString();

            assertThat(tigerProxy.getRbelMessages().get(1).findElement("$.body.webdriver.*.driver")
                .get().getRawStringContent())
                .contains("targetValue");
        }
    }

    @Test
    public void jsonInXml_longerRbelPathFailing() throws Exception {
        try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder().build());
            UnirestInstance unirestInstance = Unirest.spawnInstance()) {
            unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
            unirestInstance.get(
                    "http://localhost:" + mockServerClient.getPort() + "/read?filename=src/test/resources/combined.json")
                .asString();

            RbelOptions.activateRbelPathDebugging();
            tigerProxy.getRbelMessages().get(1)
                .findElement("$.body.xmlContent.RegistryResponse.RegistryErrorList.*.webdriver");
            RbelOptions.deactivateRbelPathDebugging();
        }
    }

    @Test
    public void jsonInXml_longerRbelPathSucceeding() throws Exception {
        try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder().build());
            UnirestInstance unirestInstance = Unirest.spawnInstance()) {
            unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
            unirestInstance.get(
                    "http://localhost:" + mockServerClient.getPort() + "/read?filename=src/test/resources/combined.json")
                .asString();

            RbelOptions.activateRbelPathDebugging();
            assertThat(tigerProxy.getRbelMessages().get(1).findElement("$..textTest.hier")
                .get().getRawStringContent())
                .isEqualTo("ist kein text");
        }
    }

    @Test
    public void forwardProxyRoute_sendMessage() throws Exception {
        try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://norealserver")
                .to("http://localhost:" + mockServerClient.getPort())
                .build()))
            .build());
            UnirestInstance unirestInstance = Unirest.spawnInstance()) {
            unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
            unirestInstance.get("http://norealserver/foo").asString();

            assertThat(tigerProxy.getRbelMessages().get(1).findElement("$.body")
                .get().getRawStringContent())
                .isEqualTo("bar");
        }
    }

    @Test
    public void forwardProxyRoute_waitForMessageSent() throws Exception {
        try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://norealserver")
                .to("http://localhost:" + mockServerClient.getPort())
                .build()))
            .build());
            UnirestInstance unirestInstance = Unirest.spawnInstance()) {
            System.out.println("curl -v http://norealserver/foo -x localhost:" + tigerProxy.getProxyPort());
            unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
            unirestInstance.get("http://norealserver/foo").asString();

            await().atMost(2, TimeUnit.SECONDS)
                .until(() -> tigerProxy.getRbelMessages().size() >= 2);
        }
    }

    @Test
    public void reverseProxyRoute_waitForMessageSent() throws Exception {
        try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + mockServerClient.getPort())
                .build()))
            .build())) {

            System.out.println("curl -v http://localhost:" + tigerProxy.getProxyPort() + "/foo");
            Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foo").asString();

            await().atMost(2, TimeUnit.SECONDS)
                .until(() -> tigerProxy.getRbelMessages().size() >= 2);
        }
    }

    @Test
    public void reverseProxyDeepRoute_waitForMessageSent() throws Exception {
        try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/wuff")
                .to("http://localhost:" + mockServerClient.getPort())
                .build()))
            .build())) {

            System.out.println("curl -v http://localhost:" + tigerProxy.getProxyPort() + "/wuff/foo");
            Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/wuff/foo").asString();

            await().atMost(2, TimeUnit.SECONDS)
                .until(() -> tigerProxy.getRbelMessages().size() >= 2);
        }
    }

    @Test
    public void reverseProxyWithTls_waitForMessageSent() throws Exception {
        try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("/")
                .to("http://localhost:" + mockServerClient.getPort())
                .build()))
            .build());
            final UnirestInstance unirestInstance = Unirest.spawnInstance();) {

            System.out.println("curl -v https://localhost:" + tigerProxy.getProxyPort() + "/foo");
            unirestInstance.config().sslContext(tigerProxy.buildSslContext());
            unirestInstance.get("https://localhost:" + tigerProxy.getProxyPort() + "/foo").asString();

            await().atMost(2, TimeUnit.SECONDS)
                .until(() -> tigerProxy.getRbelMessages().size() >= 2);
        }
    }

    @Test
    public void forwardProxyWithTls_waitForMessageSent() throws Exception {
        try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://blub")
                .to("http://localhost:" + mockServerClient.getPort())
                .build()))
            .tls(TigerTlsConfiguration.builder()
                .domainName("blub")
                .build())
            .build());
            final UnirestInstance unirestInstance = Unirest.spawnInstance();) {

            System.out.println("curl -v https://blub/foo -x http://localhost:" + tigerProxy.getProxyPort() + " -k");
            unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
            unirestInstance.config().verifySsl(false);
            unirestInstance.get("https://blub/foo").asString();

            await().atMost(2, TimeUnit.SECONDS)
                .until(() -> tigerProxy.getRbelMessages().size() >= 2);
        }
    }

    @Test
    @Disabled("Doesnt work on some JVMs (Brainpool restrictions)")
    public void forwardProxyWithTlsAndCustomCa_waitForMessageSent() throws Exception {
        try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://blub")
                .to("http://localhost:" + mockServerClient.getPort())
                .build()))
            .tls(TigerTlsConfiguration.builder()
                .serverRootCa(new TigerConfigurationPkiIdentity("../tiger-proxy/src/test/resources/customCa.p12;00"))
                .build())
            .build());
        final UnirestInstance unirestInstance = Unirest.spawnInstance();) {

            System.out.println("curl -v https://blub/foo -x http://localhost:" + tigerProxy.getProxyPort() + " -k");
            unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
            unirestInstance.config().verifySsl(false);
            unirestInstance.get("https://blub/foo").asString();

            await().atMost(2, TimeUnit.SECONDS)
                .until(() -> tigerProxy.getRbelMessages().size() >= 2);
        }
    }

    @Test
    @Disabled
    public void twoProxiesWithTrafficForwarding_shouldShowTraffic() throws Exception {
        // standalone-application starten!
        // webui ??ffnen

        try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .trafficEndpoints(List.of("http://localhost:8080"))
            .build())) {
            System.out.println("curl -v https://api.twitter.com/1.1/jot/client_event.json -x http://localhost:6666 -k");

            await().atMost(2, TimeUnit.HOURS)
                .until(() -> tigerProxy.getRbelMessages().size() >= 4);
        }
    }

    @Test
    public void modificationForReturnValue() throws Exception {
        RbelOptions.activateJexlDebugging();
        try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://blub")
                .to("http://localhost:" + mockServerClient.getPort())
                .build()))
            .modifications(List.of(RbelModificationDescription.builder()
                .targetElement("$.body")
                .replaceWith("horridoh!")
                .build()))
            .build());
            final UnirestInstance unirestInstance = Unirest.spawnInstance();) {
            @Language("yaml") String yamlConfiguration =
                "tigerProxy:\n" +
                    "  modifications:\n" +
                    "    # wird nur f??r antworten ausgef??hrt: Anfragen haben keinen statusCode (fails silently)\n" +
                    "    - targetElement: \"$.header.statusCode\"\n" +
                    "      replaceWith: \"400\"\n" +
                    "    - targetElement: \"$.body\"\n" +
                    "      condition: \"isRequest\"\n" +
                    "      replaceWith: \"my.host\"\n" +
                    "      regexFilter: \"hostToBeReplaced:\\d{3,5}\"";

            unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
            unirestInstance.get("http://blub/foo").asString();

            assertThat(tigerProxy.getRbelMessages().get(1).findElement("$.body")
                .get().getRawStringContent())
                .isEqualTo("horridoh!");
        }
    }

    @Test
    public void tslSuiteEnforcement() throws Exception {
        final String configuredSslSuite = "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA";

        try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("https://blub")
                .to("http://localhost:" + mockServerClient.getPort())
                .build()))
            .tls(TigerTlsConfiguration.builder()
                .serverSslSuites(List.of(configuredSslSuite))
                .build())
            .build());
            final UnirestInstance unirestInstance = Unirest.spawnInstance();) {

            SSLContext ctx = tigerProxy.buildSslContext();
            unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
            unirestInstance.config().sslContext(ctx);
            unirestInstance.get("https://blub/foo").asString();

            assertThat(ctx.getClientSessionContext()
                .getSession(ctx.getClientSessionContext().getIds().nextElement())
                .getCipherSuite())
                .isEqualTo(configuredSslSuite);
        }
    }
}
