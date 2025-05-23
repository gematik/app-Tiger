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

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.data.config.tigerproxy.*;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.util.List;
import java.util.stream.Stream;
import kong.unirest.core.*;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestTigerProxyRouting extends AbstractTigerProxyTest {

  @ParameterizedTest
  @CsvSource(
      textBlock =
          """
      http, http, http
      https, https, https
      http, https, http
      https, http, https
""")
  void twoHopsThroughTigerProxy_secondHopShouldAlwaysHonorToRouteChoiceOfProtocol(
      String routeFromProtocol, String routeToProtocol, String requestProtocol) {
    spawnTigerProxyWith(new TigerProxyConfiguration());
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from(routeFromProtocol + "://backend/combotest")
            .to(routeToProtocol + "://localhost:" + tigerProxy.getProxyPort() + "/foobar")
            .build());
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("/")
            .to("http://localhost:" + fakeBackendServerPort)
            .build());

    assertThat(proxyRest.get(requestProtocol + "://backend/combotest").asString().getStatus())
        .isEqualTo(666);

    awaitMessagesInTigerProxy(4);
    if (routeToProtocol.equals("https")) {
      assertThat(tigerProxy.getRbelMessagesList().get(1))
          .extractChildWithPath("$.tlsVersion")
          .asString()
          .isNotBlank();
    } else {
      assertThat(tigerProxy.getRbelMessagesList().get(1)).doesNotHaveChildWithPath("$.tlsVersion");
    }
  }

  @ParameterizedTest
  @MethodSource("nestedAndShallowPathTestCasesForwardRoute")
  void forwardProxyToNestedTarget_ShouldAdressCorrectly(
      String fromPath, String requestPath, String actualPath, int expectedReturnCode) {
    spawnTigerProxyWith(new TigerProxyConfiguration());
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("http://backend")
            .to("http://localhost:" + fakeBackendServerPort + fromPath)
            .build());

    assertThat(proxyRest.get("http://backend" + requestPath).asString().getStatus())
        .isEqualTo(expectedReturnCode);
    awaitMessagesInTigerProxy(2);
    final RbelElement request = tigerProxy.getRbelMessagesList().get(0);

    assertThat(request)
        .extractChildWithPath("$.header.Host")
        .hasStringContentEqualTo("localhost:" + fakeBackendServerPort)
        .andTheInitialElement()
        .extractChildWithPath("$.path")
        .hasStringContentEqualTo(actualPath);
  }

  @ParameterizedTest
  @MethodSource("nestedAndShallowPathTestCasesReverseRoute")
  void reverseProxyToNestedTarget_ShouldAddressCorrectly(
      String toPath, String requestPath, String actualPath, int expectedReturnCode) {
    spawnTigerProxyWith(new TigerProxyConfiguration());
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("/")
            .to("http://localhost:" + fakeBackendServerPort + toPath)
            .build());

    assertThat(
            Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + requestPath)
                .asString()
                .getStatus())
        .isEqualTo(expectedReturnCode);
    awaitMessagesInTigerProxy(2);
    final RbelElement request = tigerProxy.getRbelMessagesList().get(0);

    assertThat(request)
        .extractChildWithPath("$.header.[?(key=~'host|Host')]")
        .hasStringContentEqualTo("localhost:" + tigerProxy.getProxyPort());
    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .extractChildWithPath("$.path")
        .hasStringContentEqualTo(actualPath);
  }

  public static Stream<Arguments> nestedAndShallowPathTestCasesForwardRoute() {
    // In a forward route the request is made to http://backend and the configured route sends it to
    // http://backend/foobar
    // We expect no trailing slash in the actual path.
    // With unirest 3, the http client would silently change the original request to http://backend/
    // and therefore the actual path would be http://backend/foobar/

    return Stream.concat(
        Stream.of(Arguments.of("/foobar", "", "/foobar", 666)), nestedAndShallowPathTestCases());
  }

  public static Stream<Arguments> nestedAndShallowPathTestCasesReverseRoute() {
    // In a reverse route route is configured to redirect from "/" to "/foobar" . Because the
    // from is the single slash "/", the actual path also ends with a trailing slash.
    return Stream.concat(
        Stream.of(Arguments.of("/foobar", "", "/foobar/", 666)), nestedAndShallowPathTestCases());
  }

  public static Stream<Arguments> nestedAndShallowPathTestCases() {
    return Stream.of(
        /*
         * The cases 5, 7 & 13, 15 SHOULD result in an actual path without terminating slash.
         * However, if no deep path is set the actual HTTP request will be a "GET /", regardless
         * of the actual base path.
         */

        // toPath, requestPath, actualPath, expectedReturnCode
        Arguments.of("/deep", "/foobar", "/deep/foobar", 777),
        Arguments.of("/deep", "/foobar/", "/deep/foobar/", 777),
        Arguments.of("/deep/", "/foobar", "/deep/foobar", 777),
        Arguments.of("/deep/", "/foobar/", "/deep/foobar/", 777),
        // Arguments.of("/foobar", "", "/foobar/", 666), // 5 - special case with different handling
        // by reverse and forward routes
        Arguments.of("/foobar", "/", "/foobar/", 666),
        Arguments.of("/foobar/", "", "/foobar/", 666),
        Arguments.of("/foobar/", "/", "/foobar/", 666),
        Arguments.of("", "/foobar", "/foobar", 666), // 9
        Arguments.of("", "/foobar/", "/foobar/", 666),
        Arguments.of("/", "/foobar", "/foobar", 666),
        Arguments.of("/", "/foobar/", "/foobar/", 666),
        Arguments.of("", "", "/", 888), // 13
        Arguments.of("", "/", "/", 888),
        Arguments.of("/", "", "/", 888),
        Arguments.of("/", "/", "/", 888));
  }

  public static Stream<Arguments> trailingSlashTestCases() {
    return Stream.of(
        // fromPath, toPath, requestPath, actualPath
        Arguments.of("/webapp/", "/api/", "/webapp/foo?bar=baz", "/api/foo?bar=baz"),
        Arguments.of("/webapp/", "/api", "/webapp/foo?bar=baz", "/api/foo?bar=baz"),
        Arguments.of("/webapp", "/api/", "/webapp/foo?bar=baz", "/api/foo?bar=baz"),
        Arguments.of("/webapp", "/api", "/webapp/foo?bar=baz", "/api/foo?bar=baz"),
        // 5
        Arguments.of("/webapp/", "/api/", "/webapp/foo/?bar=baz", "/api/foo/?bar=baz"),
        Arguments.of("/webapp/", "/api", "/webapp/foo/?bar=baz", "/api/foo/?bar=baz"),
        Arguments.of("/webapp", "/api/", "/webapp/foo/?bar=baz", "/api/foo/?bar=baz"),
        Arguments.of("/webapp", "/api", "/webapp/foo/?bar=baz", "/api/foo/?bar=baz"),
        // 9
        Arguments.of("/webapp/", "/api/", "/webapp", "/api/"),
        Arguments.of("/webapp/", "/api", "/webapp", "/api"),
        Arguments.of("/webapp", "/api/", "/webapp", "/api/"),
        Arguments.of("/webapp", "/api", "/webapp", "/api"),
        // 13
        Arguments.of("/webapp/", "/api/", "/webapp/", "/api/"),
        Arguments.of("/webapp/", "/api", "/webapp/", "/api"),
        Arguments.of("/webapp", "/api/", "/webapp/", "/api/"),
        Arguments.of("/webapp", "/api", "/webapp/", "/api/"));
  }

  public static Stream<Arguments> failingTrailingSlashTestCases() {
    return Stream.of(
        // fromPath, toPath, requestPath
        Arguments.of("/webapp/", "/api/", "/webappfoo?bar=baz"),
        Arguments.of("/webapp/", "/api", "/webappfoo?bar=baz"),
        Arguments.of("/webapp", "/api/", "/webappfoo?bar=baz"),
        Arguments.of("/webapp", "/api", "/webappfoo?bar=baz"));
  }

  @ParameterizedTest
  @MethodSource("trailingSlashTestCases")
  void reverseProxyTrailingSlashTestCases(
      String fromPath,
      String toPath,
      String requestPath,
      String actualPath,
      WireMockRuntimeInfo backendServer) {
    spawnTigerProxyWith(new TigerProxyConfiguration());
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from(fromPath)
            .to("http://localhost:" + fakeBackendServerPort + toPath)
            .build());

    backendServer.getWireMock().getServeEvents().clear();

    Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + requestPath)
        .asString()
        .getStatus();
    awaitMessagesInTigerProxy(2);
    final RbelElement request = tigerProxy.getRbelMessagesList().get(0);

    assertThat(request)
        .extractChildWithPath("$.header.[?(key=~'host|Host')]")
        .hasStringContentEqualTo("localhost:" + tigerProxy.getProxyPort());
    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .extractChildWithPath("$.path")
        .hasStringContentEqualTo(actualPath);
    assertThat(backendServer.getWireMock().getServeEvents().get(0).getRequest().getAbsoluteUrl())
        .endsWith(actualPath);
  }

  @ParameterizedTest
  @MethodSource("trailingSlashTestCases")
  void forwardProxyTrailingSlashTestCases(
      String fromPath,
      String toPath,
      String requestPath,
      String actualPath,
      WireMockRuntimeInfo backendServer) {
    spawnTigerProxyWith(new TigerProxyConfiguration());
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("http://mydomain" + fromPath)
            .to("http://localhost:" + fakeBackendServerPort + toPath)
            .build());

    backendServer.getWireMock().getServeEvents().clear();

    proxyRest.get("http://mydomain" + requestPath).asString().getStatus();
    awaitMessagesInTigerProxy(2);

    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .extractChildWithPath("$.path")
        .hasStringContentEqualTo(actualPath);
    assertThat(backendServer.getWireMock().getServeEvents().get(0).getRequest().getAbsoluteUrl())
        .endsWith(actualPath);
  }

  @ParameterizedTest
  @MethodSource("failingTrailingSlashTestCases")
  void reverseProxyFailingTrailingSlashTestCases(
      String fromPath, String toPath, String requestPath) {
    spawnTigerProxyWith(new TigerProxyConfiguration());
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from(fromPath)
            .to("http://localhost:" + fakeBackendServerPort + toPath)
            .build());

    val request = Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + requestPath);
    assertThatThrownBy(request::asString).isInstanceOf(UnirestException.class);
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "/foo, /, /foo/bar, /bar",
        "/foo/bar, /, /foo/bar/schmah, /schmah",
        "/foo, /holla, /foo/bar, /holla/bar",
        "/foo/bar, /holla, /foo/bar/schmah, /holla/schmah"
      },
      delimiter = ',')
  void patternWithPathReverse_matchingPartOfRequestShouldBeStripped(
      String fromPath, String toPath, String requestPath, String shouldBecomePath) {
    spawnTigerProxyWith(new TigerProxyConfiguration());
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from(fromPath)
            .to("http://localhost:" + tigerProxy.getProxyPort() + toPath)
            .build());
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from(shouldBecomePath)
            .to("http://localhost:" + fakeBackendServerPort + "/foobar")
            .build());

    assertThat(
            Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + requestPath)
                .asString()
                .getStatus())
        .isEqualTo(666);
    awaitMessagesInTigerProxy(4);
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "/foo, /, /foo/bar, /bar",
        "/foo/bar, /, /foo/bar/schmah, /schmah",
        "/foo, /holla, /foo/bar, /holla/bar",
        "/foo/bar, /holla, /foo/bar/schmah, /holla/schmah"
      },
      delimiter = ',')
  void patternWithPathForward_matchingPartOfRequestShouldBeStripped(
      String fromPath, String toPath, String requestPath, String shouldBecomePath) {
    spawnTigerProxyWith(new TigerProxyConfiguration());
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("http://backend" + fromPath)
            .to("http://localhost:" + tigerProxy.getProxyPort() + toPath)
            .build());
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from(shouldBecomePath)
            .to("http://localhost:" + fakeBackendServerPort + "/foobar")
            .build());

    assertThat(proxyRest.get("http://backend" + requestPath).asString().getStatus()).isEqualTo(666);
    awaitMessagesInTigerProxy(4);
  }

  @Test
  void addCompetingRouteWithLowerPriorityForwardProxy_shouldWorkOnlyInSpecializedCases() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("http://backend/other")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build(),
                    TigerConfigurationRoute.builder()
                        .from("http://backend/hallo")
                        .to("http://localhost:" + fakeBackendServerPort + "/deep/foobar")
                        .build(),
                    TigerConfigurationRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort + "/foobar")
                        .build(),
                    TigerConfigurationRoute.builder()
                        .from("http://backend/else")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    assertThat(proxyRest.get("http://backend").asJson().getStatus()).isEqualTo(666);
    assertThat(proxyRest.get("http://backend/hallo").asJson().getStatus()).isEqualTo(777);
  }

  @Test
  void addCompetingRouteWithLowerPriorityReverseProxy_shouldWorkOnlyInSpecializedCases() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("/other")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build(),
                    TigerConfigurationRoute.builder()
                        .from("/hallo")
                        .to("http://localhost:" + fakeBackendServerPort + "/deep/foobar")
                        .build(),
                    TigerConfigurationRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort + "/foobar")
                        .build(),
                    TigerConfigurationRoute.builder()
                        .from("/else")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    assertThat(
            Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + "/hallo")
                .asJson()
                .getStatus())
        .isEqualTo(777);
    assertThat(Unirest.get("http://localhost:" + tigerProxy.getProxyPort()).asJson().getStatus())
        .isEqualTo(666);
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        ".*.google.de; .*.google.at; www.google.de",
        "myHost; anotherHost; myHost",
        "myHost:80; myHost:443; myHost:80",
        "myHost; myHost:80; myHost",
        "myHost; myHost:443; myHost:80",
        "anotherHost, myHost:80; myHost:443; myHost:80",
        "myHost; myHost, anotherHost:80; myHost:80",
        "myHost; anotherHost; mYHoST",
        ".*.google.de; .*.google.at; www.gOOgLE.de"
      },
      delimiter = ';')
  void routingDecisionViaHostHeader(String hostsRoute1, String hostsRoute2, String hostHeader) {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .activateForwardAllLogging(false)
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort + "/deep/foobar")
                        .hosts(List.of(hostsRoute2.split("\\,")))
                        .build(),
                    TigerConfigurationRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort + "/foobar")
                        .hosts(List.of(hostsRoute1.split("\\,")))
                        .build(),
                    TigerConfigurationRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort + "/")
                        .build()))
            .build());

    assertThat(
            unirestInstance
                .get("http://localhost:" + tigerProxy.getProxyPort() + "/")
                .header("host", hostHeader)
                .asJson()
                .getStatus())
        .isEqualTo(666); // /foobar.*
  }

  @Test
  void clashingRoutes() {
    spawnTigerProxyWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort + "/foobar")
                        .build(),
                    TigerConfigurationRoute.builder()
                        .from("http://ps")
                        .to("http://localhost:" + fakeBackendServerPort + "/deep/foobar")
                        .build()))
            .build());

    assertThat(proxyRest.get("http://ps").asString().getStatus()).isEqualTo(777); // /deep/foobar.*
    assertThat(Unirest.get("http://localhost:" + tigerProxy.getProxyPort()).asString().getStatus())
        .isEqualTo(666); // /foobar.*
  }
}
