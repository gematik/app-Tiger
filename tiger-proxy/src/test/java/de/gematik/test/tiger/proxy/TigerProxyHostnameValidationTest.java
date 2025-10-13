/*
 * Copyright [2025], gematik GmbH
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TigerProxyHostnameValidationTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://exam ple.com",
        "ht tp://example.com",
        "http://exam_ple.com",
        "://example.com",
        "http://example.com/%ZZ",
        "htp://example.com",
        "http:://example.com",
        "http:/example.com",
        "http://example.com/?q=hello world",
        "http://example.com:abc"
      })
  void addRoute_shouldThrowExceptionForDisallowedHostname(String toUrl) {
    try (TigerProxy proxy = new TigerProxy(new TigerProxyConfiguration())) {
      TigerConfigurationRoute route =
          TigerConfigurationRoute.builder().from("http://foo.bar").to(toUrl).build();

      assertThatThrownBy(() -> proxy.addRoute(route))
          .isInstanceOf(TigerConfigurationException.class)
          .hasMessageContaining("is not permitted");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"http://example.com", "https://example.com", "http://localhost"})
  void addRoute_shouldNotThrowExceptionForAllowedHostname(String toUrl) {
    try (TigerProxy proxy = new TigerProxy(new TigerProxyConfiguration())) {
      TigerConfigurationRoute route =
          TigerConfigurationRoute.builder().from("http://foo.bar").to(toUrl).build();

      assertDoesNotThrow(() -> proxy.addRoute(route));
    }
  }
}
