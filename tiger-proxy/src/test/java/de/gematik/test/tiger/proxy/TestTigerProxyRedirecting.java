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

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.util.stream.Stream;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Slf4j
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
class TestTigerProxyRedirecting extends AbstractFastTigerProxyTest {

  @ParameterizedTest
  @MethodSource("nestedAndShallowPathTestCases")
  void forwardProxyWithNestedPath_shouldRewriteLocationHeaderIfConfigured(
      String toPath, String requestPath, int statusCode) {
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("http://foo.bar")
            .to("http://localhost:" + fakeBackendServerPort + toPath)
            .build());

    assertThat(proxyRest.get("http://foo.bar" + requestPath).asString().getStatus())
        .isEqualTo(statusCode); // we should be forwarded to /deep/foobar
  }

  @ParameterizedTest
  @MethodSource("nestedAndShallowPathTestCases")
  void reverseProxyWithNestedPath_shouldRewriteLocationHeaderIfConfigured(
      String toPath, String requestPath, int statusCode) {
    tigerProxy.addRoute(
        TigerConfigurationRoute.builder()
            .from("/")
            .to("http://localhost:" + fakeBackendServerPort + toPath)
            .build());

    assertThat(
            Unirest.get("http://localhost:" + tigerProxy.getProxyPort() + requestPath)
                .asString()
                .getStatus())
        .isEqualTo(statusCode); // we should be forwarded to /deep/foobar
  }

  @Test
  void rewriteOfLocationHeaderShouldNotRemoveAdditionalHeaders() {
    // We need a new UnirestInstance to be able to change its own configuration without affecting
    // the other unit tests
    // See https://kong.github.io/unirest-java/#multiple-configurations
    // In this test we don't follow the redirects because we want to perform assertions on the first
    // response and not on the redirection.
    try (UnirestInstance unirestNoRedirects = Unirest.spawnInstance()) {
      unirestNoRedirects.config().followRedirects(false);

      tigerProxy.addRoute(
          TigerConfigurationRoute.builder()
              .from("/")
              .to("http://localhost:" + fakeBackendServerPort + "/redirect")
              .build());

      HttpResponse<String> response =
          unirestNoRedirects
              .get("http://localhost:" + tigerProxy.getProxyPort() + "/withAdditionalHeaders")
              .asString();

      assertThat(response.getStatus())
          .isEqualTo(302); // see AbstractTigerProxyTest for expected value.
      assertThat(response.getHeaders().containsKey("additional-header")).isTrue();
      assertThat(response.getHeaders().get("additional-header").get(0)).isEqualTo("test_value");
    }
  }

  public static Stream<Arguments> nestedAndShallowPathTestCases() {
    return Stream.of(
        Arguments.of("/deep", "/forward", 777),
        Arguments.of("/deep", "/forward/", 777),
        Arguments.of("/deep/", "/forward", 777),
        Arguments.of("/deep/", "/forward/", 777),
        Arguments.of("", "/forward", 666),
        Arguments.of("", "/forward/", 666),
        Arguments.of("/", "/forward", 666),
        Arguments.of("/", "/forward/", 666));
  }
}
