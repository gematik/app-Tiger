/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package de.gematik.rbellogger.data.decorator;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelSocketAddressFacet;
import de.gematik.rbellogger.util.RbelSocketAddress;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ServerNameFromKnownHostnameTest {

  private final ServerNameFromKnownHostname serverNameFromKnownHostname =
      new ServerNameFromKnownHostname();

  @ParameterizedTest
  @CsvSource({
    "backend/172.18.0.3:8080, backend",
    "popp-zeta-ingress:443, popp-zeta-ingress",
    "172.18.0.3:8080, ''",
    "localhost/127.0.0.1:80, ''",
    "127.0.0.1:80, ''"
  })
  void shouldUseRealHostnamesOnly(String address, String expectedName) {
    assertThat(serverNameFromKnownHostname.apply(addressElement(address, null)))
        .isEqualTo(java.util.Optional.ofNullable(expectedName.isEmpty() ? null : expectedName));
  }

  @ParameterizedTest
  @CsvSource({"backend/172.18.0.3:8080", "172.18.0.3:8080"})
  void shouldKeepAnExistingServerName(String address) {
    assertThat(serverNameFromKnownHostname.apply(addressElement(address, "reverseProxy")))
        .isEmpty();
  }

  private static RbelElement addressElement(String address, String bundledServerName) {
    final RbelElement element =
        RbelSocketAddressFacet.buildRbelSocketAddressFacet(
            new RbelElement(new byte[0], null),
            RbelSocketAddress.fromString(address).orElseThrow(),
            bundledServerName);
    return element;
  }
}
