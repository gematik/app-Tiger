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

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.test.tiger.common.config.TigerTypedConfigurationKey;
import de.gematik.test.tiger.common.data.config.tigerproxy.*;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.TestSocketUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

/** UnirestClient --> TigerProxy --> Squid Proxy (Docker) --> fakebackend */
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
@Slf4j
@Disabled("The host.docker.internal is not working in the CI pipeline")
class TigerProxyForwardToProxyTest extends AbstractTigerProxyTest {

  private static GenericContainer<?> squidContainer;
  private static final int SQUID_PROXY_PORT_NO_AUTH = TestSocketUtils.findAvailableTcpPort();
  private static final int SQUID_PROXY_PORT_AUTH_REQUIRED = TestSocketUtils.findAvailableTcpPort();

  public static final TigerTypedConfigurationKey<String> DOCKER_HOST =
      new TigerTypedConfigurationKey<>("tiger.docker.host", String.class, "host.docker.internal");

  @BeforeAll
  static void setUp() {
    MountableFile squidConfig = MountableFile.forHostPath("src/test/resources/squid.conf");
    MountableFile squidPassword = MountableFile.forHostPath("src/test/resources/squid.htpasswd");

    squidContainer =
        new GenericContainer<>("elestio/squid:v6.8.0")
            .withCopyFileToContainer(squidConfig, "/etc/squid/squid.conf")
            .withCopyFileToContainer(squidPassword, "/etc/squid/passwd")
            .withAccessToHost(true);

    squidContainer.setPortBindings(
        List.of(SQUID_PROXY_PORT_NO_AUTH + ":3128", SQUID_PROXY_PORT_AUTH_REQUIRED + ":3129"));
    log.info(
        "Starting squid container with port bindings {}:3128 and {}:3129",
        SQUID_PROXY_PORT_NO_AUTH,
        SQUID_PROXY_PORT_AUTH_REQUIRED);
    squidContainer.start();
  }

  @AfterAll
  static void tearDown() {
    squidContainer.stop();
  }

  static Collection<Arguments> withOrWithoutTlsParams() {
    return List.of(
        Arguments.of(
            "HTTP with proxy_auth",
            "http",
            (Supplier<Integer>) () -> fakeBackendServerPort,
            (Supplier<ForwardProxyInfo>)
                TigerProxyForwardToProxyTest::createForwardProxyConfigWithAuth),
        Arguments.of(
            "HTTPS with proxy_auth",
            "https",
            (Supplier<Integer>) () -> fakeBackendServerTlsPort,
            (Supplier<ForwardProxyInfo>)
                TigerProxyForwardToProxyTest::createForwardProxyConfigWithAuth),
        Arguments.of(
            "HTTP without authorization",
            "http",
            (Supplier<Integer>) () -> fakeBackendServerPort,
            (Supplier<ForwardProxyInfo>)
                TigerProxyForwardToProxyTest::createForwardProxyConfigNoAuth),
        Arguments.of(
            "HTTPS without authorization",
            "https",
            (Supplier<Integer>) () -> fakeBackendServerTlsPort,
            (Supplier<ForwardProxyInfo>)
                TigerProxyForwardToProxyTest::createForwardProxyConfigNoAuth));
  }

  private static ForwardProxyInfo createForwardProxyConfigWithAuth() {
    return ForwardProxyInfo.builder()
        .hostname("localhost")
        .port(SQUID_PROXY_PORT_AUTH_REQUIRED)
        .username("admin")
        .password("admin")
        .build();
  }

  private static ForwardProxyInfo createForwardProxyConfigNoAuth() {
    return ForwardProxyInfo.builder().hostname("localhost").port(SQUID_PROXY_PORT_NO_AUTH).build();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("withOrWithoutTlsParams")
  //  @Disabled
  void sendRequestToFakebackend_WithOrWithoutTls(
      String description,
      String protocol,
      Supplier<Integer> fakeBackendPort,
      Supplier<ForwardProxyInfo> forwardProxyInfo) {
    final String toRoute =
        protocol + "://" + DOCKER_HOST.getValueOrDefault() + ":" + fakeBackendPort.get();
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .tls(
                TigerTlsConfiguration.builder()
                    .masterSecretsFile("target/master-secrets.txt")
                    .build())
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder().from("https://maHost").to(toRoute).build()))
            .forwardToProxy(forwardProxyInfo.get())
            .build());

    log.info("Routing traffic to {}", toRoute);

    proxyRest.get("https://maHost/ok").asString();
    awaitMessagesInTigerProxy(2);

    assertThat(tigerProxy.getRbelMessagesList().get(1))
        .extractChildWithPath("$.responseCode")
        .hasStringContentEqualTo("200")
        .andTheInitialElement()
        .extractChildWithPath("$.body.request")
        .hasStringContentEqualTo("body");
  }

  @ParameterizedTest
  @ValueSource(strings = {"http", "https"})
  void noProxyHostInVariousSettings(String protocol) {
    executeNoProxyTestWithProtocolAndNoProxyHosts(protocol, List.of("localhost"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"http", "https"})
  void nonMatchingNoProxyHostInVariousSettings(String protocol) {
    assertThatThrownBy(
            () -> executeNoProxyTestWithProtocolAndNoProxyHosts(protocol, List.of("somethingElse")))
        .isNotNull();
  }

  void executeNoProxyTestWithProtocolAndNoProxyHosts(String protocol, List<String> noProxyHosts) {
    final String serverPort =
        String.valueOf(protocol.equals("http") ? fakeBackendServerPort : fakeBackendServerTlsPort);
    final String toRoute = protocol + "://localhost:" + serverPort;
    final ForwardProxyInfo proxyInfo =
        ForwardProxyInfo.builder()
            .hostname("localhost")
            .port(SQUID_PROXY_PORT_NO_AUTH)
            .noProxyHosts(noProxyHosts)
            .build();
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder().from("https://maHost").to(toRoute).build()))
            .forwardToProxy(proxyInfo)
            .build());

    log.info("Routing traffic to {}", toRoute);

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
