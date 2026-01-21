/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.proxy;

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static uk.org.webcompere.systemstubs.SystemStubs.tapSystemErrAndOut;

import de.gematik.test.tiger.common.data.config.tigerproxy.*;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.util.List;
import kong.unirest.core.UnirestException;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
@Slf4j
class TigerProxyForwardToProxyTest extends AbstractTigerProxyTest {

  /**
   * client -> local proxy -> forward proxy -> fakebackend
   *
   * <p>The http client sends a message to the local proxy, which is configured to use a forward
   * proxy. Given that the local proxy does not know the hostname, it would not be able to deliver
   * the message if not over the forward proxy.
   *
   * <p>The forward proxy has a route configured to deliver maHost -> localhost:
   * fakeBackendServerPort
   */
  @Test
  void sendRequestToFakebackend_WithOrWithoutTls() {
    final String toRoute = "http://localhost:" + fakeBackendServerPort;
    try (var forwardProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .name("last Proxy in chain before fake backend'")
                .proxyRoutes(
                    List.of(
                        TigerConfigurationRoute.builder()
                            .from("https://maHost")
                            .to(toRoute)
                            .build()))
                .build())) {

      spawnTigerProxyWithDefaultRoutesAndWith(
          TigerProxyConfiguration.builder()
              .tls(
                  TigerTlsConfiguration.builder()
                      .masterSecretsFile("target/master-secrets.txt")
                      .build())
              .forwardToProxy(
                  ForwardProxyInfo.builder()
                      .hostname("localhost")
                      .port(forwardProxy.getProxyPort())
                      .build())
              .build());

      log.info("Routing traffic to {}", toRoute);

      proxyRest.config().requestTimeout(10_000).connectTimeout(10_000).verifySsl(false);
      proxyRest.get("https://maHost/ok").asString();
      awaitMessagesInTigerProxy(2);

      assertThat(tigerProxy.getRbelMessagesList().get(1))
          .extractChildWithPath("$.responseCode")
          .hasStringContentEqualTo("200")
          .andTheInitialElement()
          .extractChildWithPath("$.body.request")
          .hasStringContentEqualTo("body");
    }
  }

  @Test
  void sendRequestToUnresolvable_noProxyHost_shouldNotThrowNPE() throws Exception {
    try (var forwardProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .name("last Proxy in chain before fake backend'")
                .build())) {

      spawnTigerProxyWithDefaultRoutesAndWith(
          TigerProxyConfiguration.builder()
              .forwardToProxy(
                  ForwardProxyInfo.builder()
                      .hostname("localhost")
                      .port(forwardProxy.getProxyPort())
                      .noProxyHosts(
                          List.of(
                              "notresolvable",
                              "www.example.com")) // we need both, because the NPE would only occur
                      // when
                      // comparing the notresolvable with a resolvable name. See
                      // de.gematik.test.tiger.util.NoProxyUtils.shouldUseProxyForHost
                      .build())
              .build());

      proxyRest.config().requestTimeout(10_000).connectTimeout(10_000).verifySsl(false);
      // The unirest throws its own exception. The NullPointerException or UnknownHostException are
      // thrown
      // by the tiger-proxy, but here we only see them in the log, not as a return value.
      // We still expect an UnknownHostException to be logged, because we are telling the
      // tiger
      // proxy to not use
      // the forward proxy with the notresolvable hostname, and therefore it cant deliver the
      // message to it.
      var output =
          tapSystemErrAndOut(
              () ->
                  assertThatExceptionOfType(UnirestException.class)
                      .isThrownBy(() -> proxyRest.get("http://notresolvable").asString())
                      .withMessageContaining(
                          "java.io.IOException: HTTP/1.1 header parser received no bytes"));

      Assertions.assertThat(output)
          .doesNotContain("NullPointerException")
          .contains("Caused by: java.net.UnknownHostException: notresolvable");
    }
  }

  @Test
  void sendRequestViaProxyWithUserNameAndPassword_shouldHaveCredentialsInRequest() {
    try (var forwardProxy = new TigerProxy(TigerProxyConfiguration.builder().build())) {

      spawnTigerProxyWithDefaultRoutesAndWith(
          TigerProxyConfiguration.builder()
              .forwardToProxy(
                  ForwardProxyInfo.builder()
                      .hostname("localhost")
                      .port(forwardProxy.getProxyPort())
                      .username("testUsername")
                      .password("testPassword")
                      .build())
              .build());
      proxyRest.get("http://www.example.com").asString();
      awaitMessagesInTigerProxy(2);

      var request = tigerProxy.getRbelMessagesList().get(0);
      var requestSeenByForwardProxy = forwardProxy.getRbelMessagesList().get(0);
      var response = tigerProxy.getRbelMessagesList().get(1);
      var responseSeenByForwardProxy = forwardProxy.getRbelMessagesList().get(1);

      assertThat(request).doesNotHaveChildWithPath("$.header.proxy-authorization");
      assertThat(requestSeenByForwardProxy)
          .extractChildWithPath("$.header.proxy-authorization")
          .hasStringContentEqualTo("Basic " + encodeBasicAuth("testUsername", "testPassword"));
      assertThat(responseSeenByForwardProxy)
          .extractChildWithPath("$.responseCode")
          .hasStringContentEqualTo("200")
          .andTheInitialElement()
          .extractChildWithPath("$.body.html.head.title.text")
          .hasStringContentEqualTo("Example Domain");
      assertThat(response)
          .extractChildWithPath("$.responseCode")
          .hasStringContentEqualTo("200")
          .andTheInitialElement()
          .extractChildWithPath("$.body.html.head.title.text")
          .hasStringContentEqualTo("Example Domain");
    }
  }

  public static String encodeBasicAuth(String username, String password) {
    String credentials = username + ":" + password;
    return java.util.Base64.getEncoder()
        .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
