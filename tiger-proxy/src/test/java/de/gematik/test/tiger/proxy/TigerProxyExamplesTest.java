/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.proxy;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.gematik.test.tiger.common.config.RbelModificationDescription;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerTlsConfiguration;
import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.io.File;
import java.util.List;
import javax.net.ssl.SSLContext;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
@ResetTigerConfiguration
@WireMockTest
class TigerProxyExamplesTest {

  @SneakyThrows
  @BeforeEach
  public void beforeEachLifecyleMethod(WireMockRuntimeInfo runtimeInfo) {
    runtimeInfo.getWireMock().register(get("/foo").willReturn(ok().withBody("bar")));
    runtimeInfo
        .getWireMock()
        .register(
            get(urlMatching("/foo.*"))
                .willReturn(
                    ok().withBody("bar{{request.query.echo}}")
                        .withTransformers("response-template")));
    runtimeInfo
        .getWireMock()
        .register(
            get("/read/test.json")
                .willReturn(
                    ok().withBody(
                            FileUtils.readFileToByteArray(
                                new File("src/test/resources/test.json")))));
    runtimeInfo
        .getWireMock()
        .register(
            get("/read/combined.json")
                .willReturn(
                    ok().withBody(
                            FileUtils.readFileToByteArray(
                                new File("src/test/resources/combined.json")))));
  }

  @Test
  void directTest(WireMockRuntimeInfo runtimeInfo) {
    final HttpResponse<String> response =
        Unirest.get("http://localhost:" + runtimeInfo.getHttpPort() + "/foo").asString();

    assertThat(response.getBody()).isEqualTo("bar");
  }

  @Test
  void simpleTigerProxyTest(WireMockRuntimeInfo runtimeInfo) {
    try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder().build())) {
      final UnirestInstance unirestInstance = Unirest.spawnInstance();
      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance
          .get("http://localhost:" + runtimeInfo.getHttpPort() + "/foo?echo=schmoolildu")
          .asString();
      TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessages(tigerProxy, 2);

      assertThat(tigerProxy.getRbelMessagesList().get(1))
          .extractChildWithPath("$.body")
          .hasStringContentEqualTo("barschmoolildu");
    }
  }

  @Test
  void rbelPath_getBody(WireMockRuntimeInfo runtimeInfo) {
    try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder().build());
        UnirestInstance unirestInstance = Unirest.spawnInstance()) {
      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance
          .get("http://localhost:" + runtimeInfo.getHttpPort() + "/foo?echo=schmoolildu")
          .asString();
      TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessages(tigerProxy, 2);

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
  void json_demoWithExtendedRbelPath(WireMockRuntimeInfo runtimeInfo) {
    try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder().build());
        UnirestInstance unirestInstance = Unirest.spawnInstance()) {
      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance
          .get("http://localhost:" + runtimeInfo.getHttpPort() + "/read/test.json")
          .asString();
      TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessages(tigerProxy, 2);

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
  void jsonInXml_longerRbelPathSucceeding(WireMockRuntimeInfo runtimeInfo) {
    try (TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder().build());
        UnirestInstance unirestInstance = Unirest.spawnInstance()) {
      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance
          .get("http://localhost:" + runtimeInfo.getHttpPort() + "/read/combined.json")
          .asString();
      TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessages(tigerProxy, 2);

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
  void forwardProxyRoute_sendMessage(WireMockRuntimeInfo runtimeInfo) {
    try (TigerProxy tigerProxy =
            new TigerProxy(
                TigerProxyConfiguration.builder()
                    .proxyRoutes(
                        List.of(
                            TigerConfigurationRoute.builder()
                                .from("http://norealserver")
                                .to("http://localhost:" + runtimeInfo.getHttpPort())
                                .build()))
                    .build());
        UnirestInstance unirestInstance = Unirest.spawnInstance()) {
      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance.get("http://norealserver/foo").asString();
      TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessages(tigerProxy, 2);

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
  @SuppressWarnings("java:S2699")
  void forwardProxyRoute_waitForMessageSent(WireMockRuntimeInfo runtimeInfo) {
    try (TigerProxy tigerProxy =
            new TigerProxy(
                TigerProxyConfiguration.builder()
                    .proxyRoutes(
                        List.of(
                            TigerConfigurationRoute.builder()
                                .from("http://norealserver")
                                .to("http://localhost:" + runtimeInfo.getHttpPort())
                                .build()))
                    .build());
        UnirestInstance unirestInstance = Unirest.spawnInstance()) {
      System.out.println(
          "curl -v http://norealserver/foo -x localhost:" + tigerProxy.getProxyPort());
      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance.get("http://norealserver/foo").asString();

      TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessages(tigerProxy, 2);
    }
  }

  @Test
  @SuppressWarnings("java:S2699")
  void reverseProxyRoute_waitForMessageSent(WireMockRuntimeInfo runtimeInfo) {
    try (TigerProxy tigerProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .proxyRoutes(
                    List.of(
                        TigerConfigurationRoute.builder()
                            .from("/")
                            .to("http://localhost:" + runtimeInfo.getHttpPort())
                            .build()))
                .build())) {

      System.out.println("curl -v http://localhost:" + tigerProxy.getProxyPort() + "/foo");
      Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/foo").asString();

      TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessages(tigerProxy, 2);
    }
  }

  @Test
  @SuppressWarnings("java:S2699")
  void reverseProxyDeepRoute_waitForMessageSent(WireMockRuntimeInfo runtimeInfo) {
    try (TigerProxy tigerProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .proxyRoutes(
                    List.of(
                        TigerConfigurationRoute.builder()
                            .from("/wuff")
                            .to("http://localhost:" + runtimeInfo.getHttpPort())
                            .build()))
                .build())) {

      System.out.println("curl -v http://localhost:" + tigerProxy.getProxyPort() + "/wuff/foo");
      Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/wuff/foo").asString();

      TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessages(tigerProxy, 2);
    }
  }

  @Test
  @SuppressWarnings("java:S2699")
  void reverseProxyWithTls_waitForMessageSent(WireMockRuntimeInfo runtimeInfo) {
    try (TigerProxy tigerProxy =
            new TigerProxy(
                TigerProxyConfiguration.builder()
                    .proxyRoutes(
                        List.of(
                            TigerConfigurationRoute.builder()
                                .from("/")
                                .to("http://localhost:" + runtimeInfo.getHttpPort())
                                .build()))
                    .build());
        final UnirestInstance unirestInstance = Unirest.spawnInstance()) {

      System.out.println("curl -v https://localhost:" + tigerProxy.getProxyPort() + "/foo");
      unirestInstance.config().sslContext(tigerProxy.buildSslContext());
      unirestInstance.get("https://localhost:" + tigerProxy.getProxyPort() + "/foo").asString();

      TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessages(tigerProxy, 2);
    }
  }

  @Test
  @SuppressWarnings("java:S2699")
  void forwardProxyWithTls_waitForMessageSent(WireMockRuntimeInfo runtimeInfo) {
    try (TigerProxy tigerProxy =
            new TigerProxy(
                TigerProxyConfiguration.builder()
                    .proxyRoutes(
                        List.of(
                            TigerConfigurationRoute.builder()
                                .from("https://blub")
                                .to("http://localhost:" + runtimeInfo.getHttpPort())
                                .build()))
                    .tls(TigerTlsConfiguration.builder().domainName("blub").build())
                    .build());
        final UnirestInstance unirestInstance = Unirest.spawnInstance()) {

      System.out.println(
          "curl -v https://blub/foo -x http://localhost:" + tigerProxy.getProxyPort() + " -k");
      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance.config().verifySsl(false);
      unirestInstance.get("https://blub/foo").asString();

      TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessages(tigerProxy, 2);
    }
  }

  @Test
  @Disabled("Doesnt work on some JVMs (Brainpool restrictions)")
  void forwardProxyWithTlsAndCustomCa_waitForMessageSent(WireMockRuntimeInfo runtimeInfo) {
    try (TigerProxy tigerProxy =
            new TigerProxy(
                TigerProxyConfiguration.builder()
                    .proxyRoutes(
                        List.of(
                            TigerConfigurationRoute.builder()
                                .from("https://blub")
                                .to("http://localhost:" + runtimeInfo.getHttpPort())
                                .build()))
                    .tls(
                        TigerTlsConfiguration.builder()
                            .serverRootCa(
                                new TigerConfigurationPkiIdentity(
                                    "../tiger-proxy/src/test/resources/customCa.p12;00"))
                            .build())
                    .build());
        final UnirestInstance unirestInstance = Unirest.spawnInstance()) {

      System.out.println(
          "curl -v https://blub/foo -x http://localhost:" + tigerProxy.getProxyPort() + " -k");
      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance.config().verifySsl(false);
      unirestInstance.get("https://blub/foo").asString();

      TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessages(tigerProxy, 2);
    }
  }

  @Test
  @Disabled("Needs manual opening of web ui")
  @SuppressWarnings("java:S2699")
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

      TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessages(tigerProxy, 4);
    }
  }

  @Test
  void modificationForReturnValue(WireMockRuntimeInfo runtimeInfo) {
    try (TigerProxy tigerProxy =
            new TigerProxy(
                TigerProxyConfiguration.builder()
                    .proxyRoutes(
                        List.of(
                            TigerConfigurationRoute.builder()
                                .from("http://blub")
                                .to("http://localhost:" + runtimeInfo.getHttpPort())
                                .build()))
                    .modifications(
                        List.of(
                            RbelModificationDescription.builder()
                                .targetElement("$.body")
                                .replaceWith("horridoh!")
                                .build()))
                    .build());
        final UnirestInstance unirestInstance = Unirest.spawnInstance()) {

      unirestInstance.config().proxy("localhost", tigerProxy.getProxyPort());
      unirestInstance.get("http://blub/foo").asString();
      TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessages(tigerProxy, 2);

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
  void tslSuiteEnforcement(WireMockRuntimeInfo runtimeInfo) {
    final String configuredSslSuite = "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA";

    try (TigerProxy tigerProxy =
            new TigerProxy(
                TigerProxyConfiguration.builder()
                    .proxyRoutes(
                        List.of(
                            TigerConfigurationRoute.builder()
                                .from("https://blub")
                                .to("http://localhost:" + runtimeInfo.getHttpPort())
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
}
