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

package de.gematik.rbellogger.data.decorator;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.core.RbelHostnameFacet;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ServerNameFromHostnameTest {

  private final ServerNameFromHostname serverNameFromHostname = new ServerNameFromHostname();

  @Test
  void shouldReturnTigerProxyForLocalhost() {
    RbelElement hostnameElement =
        RbelHostnameFacet.buildRbelHostnameFacet(null, new RbelHostname("127.0.0.1", 1234));

    Optional<String> result = serverNameFromHostname.apply(hostnameElement);

    assertThat(result).hasValue("local client");
  }

  @Test
  void shouldReturnRealAddressForNonLocalhost() {
    RbelElement hostnameElement =
        RbelHostnameFacet.buildRbelHostnameFacet(null, new RbelHostname("exampleTestServer", 1234));

    Optional<String> result = serverNameFromHostname.apply(hostnameElement);

    assertThat(result).hasValue("exampleTestServer");
  }
}
