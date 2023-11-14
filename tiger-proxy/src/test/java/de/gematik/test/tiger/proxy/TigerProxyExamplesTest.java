/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import de.gematik.test.tiger.common.config.RbelModificationDescription;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerTlsConfiguration;
import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;

@RequiredArgsConstructor
@ResetTigerConfiguration
@ExtendWith(MockServerExtension.class)
public class TigerProxyExamplesTest {

  @BeforeAll
  public static void beforeEachLifecyleMethod(MockServerClient mockServerClient) {
    mockServerClient
        .when(request().withPath("/foo"))
        .respond(
            httpRequest ->
                response().withBody("bar" + httpRequest.getFirstQueryStringParameter("echo")));

    mockServerClient
        .when(request().withPath("/read"))
        .respond(
            httpRequest ->
                response()
                    .withBody(
                        FileUtils.readFileToByteArray(
                            new File(httpRequest.getFirstQueryStringParameter("filename")))));
  }

  @Test
  void directTest(MockServerClient mockServerClient) {
    final HttpResponse<String> response =
        Unirest.get("http://localhost:" + mockServerClient.getPort() + "/foo").asString();

    assertThat(response.getBody()).isEqualTo("bar");
  }

  @Test
  void simpleTigerProxyTest(MockServerClient mockServerClient) {
    try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder().build())) {
      final UnirestInstance unirestInstance = Unirest.spawnInstance();
      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance
          .get("http://localhost:" + mockServerClient.getPort() + "/foo?echo=schmoolildu")
          .asString();
      waitUntilMessagesAreParsedInTheTigerProxy(tigerProxy);

      assertThat(tigerProxy.getRbelMessagesList().get(1).getRawStringContent())
          .contains("barschmoolildu");
    }
  }

  @Test
  void rbelPath_getBody(MockServerClient mockServerClient) {
    try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder().build());
        UnirestInstance unirestInstance = Unirest.spawnInstance()) {
      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance
          .get("http://localhost:" + mockServerClient.getPort() + "/foo?echo=schmoolildu")
          .asString();
      waitUntilMessagesAreParsedInTheTigerProxy(tigerProxy);

      assertThat(
              tigerProxy
                  .getRbelMessagesList()
                  .get(1)
                  .findElement("$.body")
                  .get()
                  .getRawStringContent())
          .isEqualTo("barschmoolildu");
    }
  }

  @Test
  void json_demoWithExtendedRbelPath(MockServerClient mockServerClient) {
    try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder().build());
        UnirestInstance unirestInstance = Unirest.spawnInstance()) {
      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance
          .get(
              "http://localhost:"
                  + mockServerClient.getPort()
                  + "/read?filename=src/test/resources/test.json")
          .asString();
      waitUntilMessagesAreParsedInTheTigerProxy(tigerProxy);

      assertThat(
              tigerProxy
                  .getRbelMessagesList()
                  .get(1)
                  .findElement("$.body.webdriver.*.driver")
                  .get()
                  .getRawStringContent())
          .contains("targetValue");
    }
  }

  @Test
  void jsonInXml_longerRbelPathSucceeding(MockServerClient mockServerClient) {
    try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder().build());
        UnirestInstance unirestInstance = Unirest.spawnInstance()) {
      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance
          .get(
              "http://localhost:"
                  + mockServerClient.getPort()
                  + "/read?filename=src/test/resources/combined.json")
          .asString();
      waitUntilMessagesAreParsedInTheTigerProxy(tigerProxy);

      assertThat(
              tigerProxy
                  .getRbelMessagesList()
                  .get(1)
                  .findElement("$..textTest.hier")
                  .get()
                  .getRawStringContent())
          .isEqualTo("ist kein text");
    }
  }

  @Test
  void forwardProxyRoute_sendMessage(MockServerClient mockServerClient) {
    try (TigerProxy tigerProxy =
            new TigerProxy(
                TigerProxyConfiguration.builder()
                    .proxyRoutes(
                        List.of(
                            TigerRoute.builder()
                                .from("http://norealserver")
                                .to("http://localhost:" + mockServerClient.getPort())
                                .build()))
                    .build());
        UnirestInstance unirestInstance = Unirest.spawnInstance()) {
      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance.get("http://norealserver/foo").asString();
      waitUntilMessagesAreParsedInTheTigerProxy(tigerProxy);

      assertThat(
              tigerProxy
                  .getRbelMessagesList()
                  .get(1)
                  .findElement("$.body")
                  .get()
                  .getRawStringContent())
          .isEqualTo("bar");
    }
  }

  @Test
  void forwardProxyRoute_waitForMessageSent(MockServerClient mockServerClient) {
    try (TigerProxy tigerProxy =
            new TigerProxy(
                TigerProxyConfiguration.builder()
                    .proxyRoutes(
                        List.of(
                            TigerRoute.builder()
                                .from("http://norealserver")
                                .to("http://localhost:" + mockServerClient.getPort())
                                .build()))
                    .build());
        UnirestInstance unirestInstance = Unirest.spawnInstance()) {
      System.out.println(
          "curl -v http://norealserver/foo -x localhost:" + tigerProxy.getProxyPort());
      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance.get("http://norealserver/foo").asString();

      await().atMost(2, TimeUnit.SECONDS).until(() -> tigerProxy.getRbelMessagesList().size() >= 2);
    }
  }

  @Test
  void reverseProxyRoute_waitForMessageSent(MockServerClient mockServerClient) {
    try (TigerProxy tigerProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .proxyRoutes(
                    List.of(
                        TigerRoute.builder()
                            .from("/")
                            .to("http://localhost:" + mockServerClient.getPort())
                            .build()))
                .build())) {

      System.out.println("curl -v http://localhost:" + tigerProxy.getProxyPort() + "/foo");
      Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foo").asString();

      await().atMost(2, TimeUnit.SECONDS).until(() -> tigerProxy.getRbelMessagesList().size() >= 2);
    }
  }

  @Test
  void reverseProxyDeepRoute_waitForMessageSent(MockServerClient mockServerClient) {
    try (TigerProxy tigerProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .proxyRoutes(
                    List.of(
                        TigerRoute.builder()
                            .from("/wuff")
                            .to("http://localhost:" + mockServerClient.getPort())
                            .build()))
                .build())) {

      System.out.println("curl -v http://localhost:" + tigerProxy.getProxyPort() + "/wuff/foo");
      Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/wuff/foo").asString();

      await().atMost(2, TimeUnit.SECONDS).until(() -> tigerProxy.getRbelMessagesList().size() >= 2);
    }
  }

  @Test
  void reverseProxyWithTls_waitForMessageSent(MockServerClient mockServerClient) {
    try (TigerProxy tigerProxy =
            new TigerProxy(
                TigerProxyConfiguration.builder()
                    .proxyRoutes(
                        List.of(
                            TigerRoute.builder()
                                .from("/")
                                .to("http://localhost:" + mockServerClient.getPort())
                                .build()))
                    .build());
        final UnirestInstance unirestInstance = Unirest.spawnInstance(); ) {

      System.out.println("curl -v https://localhost:" + tigerProxy.getProxyPort() + "/foo");
      unirestInstance.config().sslContext(tigerProxy.buildSslContext());
      unirestInstance.get("https://localhost:" + tigerProxy.getProxyPort() + "/foo").asString();

      await().atMost(2, TimeUnit.SECONDS).until(() -> tigerProxy.getRbelMessagesList().size() >= 2);
    }
  }

  @Test
  void forwardProxyWithTls_waitForMessageSent(MockServerClient mockServerClient) {
    try (TigerProxy tigerProxy =
            new TigerProxy(
                TigerProxyConfiguration.builder()
                    .proxyRoutes(
                        List.of(
                            TigerRoute.builder()
                                .from("https://blub")
                                .to("http://localhost:" + mockServerClient.getPort())
                                .build()))
                    .tls(TigerTlsConfiguration.builder().domainName("blub").build())
                    .build());
        final UnirestInstance unirestInstance = Unirest.spawnInstance(); ) {

      System.out.println(
          "curl -v https://blub/foo -x http://localhost:" + tigerProxy.getProxyPort() + " -k");
      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance.config().verifySsl(false);
      unirestInstance.get("https://blub/foo").asString();

      await().atMost(2, TimeUnit.SECONDS).until(() -> tigerProxy.getRbelMessagesList().size() >= 2);
    }
  }

  @Test
  @Disabled("Doesnt work on some JVMs (Brainpool restrictions)")
  void forwardProxyWithTlsAndCustomCa_waitForMessageSent(MockServerClient mockServerClient) {
    try (TigerProxy tigerProxy =
            new TigerProxy(
                TigerProxyConfiguration.builder()
                    .proxyRoutes(
                        List.of(
                            TigerRoute.builder()
                                .from("https://blub")
                                .to("http://localhost:" + mockServerClient.getPort())
                                .build()))
                    .tls(
                        TigerTlsConfiguration.builder()
                            .serverRootCa(
                                new TigerConfigurationPkiIdentity(
                                    "../tiger-proxy/src/test/resources/customCa.p12;00"))
                            .build())
                    .build());
        final UnirestInstance unirestInstance = Unirest.spawnInstance(); ) {

      System.out.println(
          "curl -v https://blub/foo -x http://localhost:" + tigerProxy.getProxyPort() + " -k");
      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance.config().verifySsl(false);
      unirestInstance.get("https://blub/foo").asString();

      await().atMost(2, TimeUnit.SECONDS).until(() -> tigerProxy.getRbelMessagesList().size() >= 2);
    }
  }

  @Test
  @Disabled
  void twoProxiesWithTrafficForwarding_shouldShowTraffic() {
    // standalone-application starten!
    // webui öffnen

    try (TigerProxy tigerProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .trafficEndpoints(List.of("http://localhost:8080"))
                .build())) {
      System.out.println(
          "curl -v https://api.twitter.com/1.1/jot/client_event.json -x http://localhost:6666 -k");

      await().atMost(2, TimeUnit.HOURS).until(() -> tigerProxy.getRbelMessagesList().size() >= 4);
    }
  }

  @Test
  void modificationForReturnValue(MockServerClient mockServerClient) {
    try (TigerProxy tigerProxy =
            new TigerProxy(
                TigerProxyConfiguration.builder()
                    .proxyRoutes(
                        List.of(
                            TigerRoute.builder()
                                .from("http://blub")
                                .to("http://localhost:" + mockServerClient.getPort())
                                .build()))
                    .modifications(
                        List.of(
                            RbelModificationDescription.builder()
                                .targetElement("$.body")
                                .replaceWith("horridoh!")
                                .build()))
                    .build());
        final UnirestInstance unirestInstance = Unirest.spawnInstance(); ) {
      @Language("yaml")
      String yamlConfiguration =
          "tigerProxy:\n"
              + "  modifications:\n"
              + "    # wird nur für antworten ausgeführt: Anfragen haben keinen statusCode (fails"
              + " silently)\n"
              + "    - targetElement: \"$.header.statusCode\"\n"
              + "      replaceWith: \"400\"\n"
              + "    - targetElement: \"$.body\"\n"
              + "      condition: \"isRequest\"\n"
              + "      replaceWith: \"my.host\"\n"
              + "      regexFilter: \"hostToBeReplaced:\\d{3,5}\"";

      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance.get("http://blub/foo").asString();
      waitUntilMessagesAreParsedInTheTigerProxy(tigerProxy);

      assertThat(
              tigerProxy
                  .getRbelMessagesList()
                  .get(1)
                  .findElement("$.body")
                  .get()
                  .getRawStringContent())
          .isEqualTo("horridoh!");
    }
  }

  @Test
  void tslSuiteEnforcement(MockServerClient mockServerClient) {
    final String configuredSslSuite = "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA";

    try (TigerProxy tigerProxy =
            new TigerProxy(
                TigerProxyConfiguration.builder()
                    .proxyRoutes(
                        List.of(
                            TigerRoute.builder()
                                .from("https://blub")
                                .to("http://localhost:" + mockServerClient.getPort())
                                .build()))
                    .tls(
                        TigerTlsConfiguration.builder()
                            .serverSslSuites(List.of(configuredSslSuite))
                            .build())
                    .build());
        final UnirestInstance unirestInstance = Unirest.spawnInstance(); ) {

      SSLContext ctx = tigerProxy.buildSslContext();
      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance.config().sslContext(ctx);
      unirestInstance.get("https://blub/foo").asString();

      assertThat(
              ctx.getClientSessionContext()
                  .getSession(ctx.getClientSessionContext().getIds().nextElement())
                  .getCipherSuite())
          .isEqualTo(configuredSslSuite);
    }
  }

  private static void waitUntilMessagesAreParsedInTheTigerProxy(TigerProxy tigerProxy) {
    await().until(() -> tigerProxy.getRbelMessages().size() >= 2);
  }
}
