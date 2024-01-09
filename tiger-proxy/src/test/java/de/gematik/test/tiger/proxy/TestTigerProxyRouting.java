/*
 * Copyright (c) 2024 gematik GmbH
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

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.data.config.tigerproxy.*;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.util.stream.Stream;
import kong.unirest.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestTigerProxyRouting extends AbstractFastTigerProxyTest {

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
        Arguments.of("/deep", "/foobar", "/deep/foobar", 777),
        Arguments.of("/deep", "/foobar/", "/deep/foobar/", 777),
        Arguments.of("/deep/", "/foobar", "/deep/foobar", 777),
        Arguments.of("/deep/", "/foobar/", "/deep/foobar/", 777),
        // "/foobar,'','/foobar'", //TODO TGR-949: mockserver-bug. should work
        Arguments.of("/foobar", "/", "/foobar/", 666),
        Arguments.of("/foobar/", "", "/foobar/", 666),
        Arguments.of("/foobar/", "/", "/foobar/", 666),
        Arguments.of("", "/foobar", "/foobar", 666),
        Arguments.of("", "/foobar/", "/foobar/", 666),
        Arguments.of("/", "/foobar", "/foobar", 666),
        Arguments.of("/", "/foobar/", "/foobar/", 666),
        // "'','',''", //TODO TGR-949: mockserver-bug. should work
        Arguments.of("", "/", "/", 888),
        Arguments.of("/", "", "/", 888),
        Arguments.of("/", "/", "/", 888));
  }
}
