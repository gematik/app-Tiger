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

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.data.config.tigerproxy.*;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.util.List;
import java.util.stream.Stream;
import kong.unirest.*;
import lombok.extern.slf4j.Slf4j;
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
class TestTigerProxyRouting extends AbstractFastTigerProxyTest {

  @ParameterizedTest
  @CsvSource(
      textBlock =
          """
      http, http, http
      https, https, https
      http, https, http
      https, http, https
""")
  void testHttpsAndHttpCombinations(
      String routeFromProtocol, String routeToProtocol, String requestProtocol) {
    tigerProxy.addRoute(
        TigerRoute.builder()
            .from(routeFromProtocol + "://backend/combotest")
            .to(routeToProtocol + "://localhost:" + tigerProxy.getProxyPort() + "/foobar")
            .build());
    tigerProxy.addRoute(
        TigerRoute.builder().from("/").to("http://localhost:" + fakeBackendServerPort).build());

    assertThat(proxyRest.get(requestProtocol + "://backend/combotest").asString().getStatus())
        .isEqualTo(666);

    awaitMessagesInTiger(4);
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
  @MethodSource("nestedAndShallowPathTestCases")
  void forwardProxyToNestedTarget_ShouldAdressCorrectly(
      String fromPath, String requestPath, String actualPath, int expectedReturnCode) {
    tigerProxy.addRoute(
        TigerRoute.builder()
            .from("http://backend")
            .to("http://localhost:" + fakeBackendServerPort + fromPath)
            .build());

    assertThat(proxyRest.get("http://backend" + requestPath).asString().getStatus())
        .isEqualTo(expectedReturnCode);
    awaitMessagesInTiger(2);
    final RbelElement request = tigerProxy.getRbelMessagesList().get(0);

    // the extractChildWithPath will return an element inside of the original to asserted element,
    // consecutive calls in an assertion chain would fail as they wouldn't start from the root
    // element but from
    // the child extracted by the first assertion
    assertThat(request) // NOSONAR
        .extractChildWithPath("$.header.Host")
        .hasStringContentEqualTo("localhost:" + fakeBackendServerPort);
    assertThat(request).extractChildWithPath("$.path").hasStringContentEqualTo(actualPath);
  }

  @ParameterizedTest
  @MethodSource("nestedAndShallowPathTestCases")
  void reverseProxyToNestedTarget_ShouldAddressCorrectly(
      String fromPath, String requestPath, String actualPath, int expectedReturnCode) {
    tigerProxy.addRoute(
        TigerRoute.builder()
            .from("/")
            .to("http://localhost:" + fakeBackendServerPort + fromPath)
            .build());

    assertThat(
            Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + requestPath)
                .asString()
                .getStatus())
        .isEqualTo(expectedReturnCode);
    awaitMessagesInTiger(2);
    final RbelElement request = tigerProxy.getRbelMessagesList().get(0);

    assertThat(request)
        .extractChildWithPath("$.header.[?(key=~'host|Host')]")
        .hasStringContentEqualTo("localhost:" + tigerProxy.getProxyPort());
    assertThat(tigerProxy.getRbelMessagesList().get(0))
        .extractChildWithPath("$.path")
        .hasStringContentEqualTo(actualPath);
  }

  public static Stream<Arguments> nestedAndShallowPathTestCases() {
    return Stream.of(
        // fromPath, requestPath, actualPath, expectedReturnCode
        Arguments.of("/deep", "/foobar", "/deep/foobar", 777),
        Arguments.of("/deep", "/foobar/", "/deep/foobar/", 777),
        Arguments.of("/deep/", "/foobar", "/deep/foobar", 777),
        Arguments.of("/deep/", "/foobar/", "/deep/foobar/", 777),
        Arguments.of("/foobar", "", "/foobar", 666), // 5
        Arguments.of("/foobar", "/", "/foobar", 666),
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

  @Test
  void addCompetingRouteWithLowerPriorityForwardProxy_shouldWorkOnlyInSpecializedCases() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("http://backend/other")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build(),
                    TigerRoute.builder()
                        .from("http://backend/hallo")
                        .to("http://localhost:" + fakeBackendServerPort + "/deep/foobar")
                        .build(),
                    TigerRoute.builder()
                        .from("http://backend")
                        .to("http://localhost:" + fakeBackendServerPort + "/foobar")
                        .build(),
                    TigerRoute.builder()
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
                    TigerRoute.builder()
                        .from("/other")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build(),
                    TigerRoute.builder()
                        .from("/hallo")
                        .to("http://localhost:" + fakeBackendServerPort + "/deep/foobar")
                        .build(),
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort + "/foobar")
                        .build(),
                    TigerRoute.builder()
                        .from("/else")
                        .to("http://localhost:" + fakeBackendServerPort)
                        .build()))
            .build());

    assertThat(
            proxyRest
                .get("http://localhost:" + tigerProxy.getProxyPort() + "/hallo")
                .asJson()
                .getStatus())
        .isEqualTo(777);
    assertThat(proxyRest.get("http://localhost:" + tigerProxy.getProxyPort()).asJson().getStatus())
        .isEqualTo(666);
  }

  @Test
  void routingDecisionViaHostHeader() {
    spawnTigerProxyWithDefaultRoutesAndWith(
        TigerProxyConfiguration.builder()
            .proxyRoutes(
                List.of(
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort + "/foobar")
                        .criterions(List.of("$.header.host =~ '.*.google.de'"))
                        .build(),
                    TigerRoute.builder()
                        .from("/")
                        .to("http://localhost:" + fakeBackendServerPort + "/deep/foobar")
                        .hosts(List.of(".*.google.at"))
                        .build()))
            .build());

    assertThat(
            proxyRest
                .get("http://localhost:" + tigerProxy.getProxyPort() + "/")
                .header("host", "www.google.de")
                .asJson()
                .getStatus())
        .isEqualTo(666); // /foobar.*
    assertThat(proxyRest.get("http://localhost:" + tigerProxy.getProxyPort())
      .header("host", "www.google.at").asJson().getStatus())
        .isEqualTo(777); // /deep/foobar.*
  }
}
