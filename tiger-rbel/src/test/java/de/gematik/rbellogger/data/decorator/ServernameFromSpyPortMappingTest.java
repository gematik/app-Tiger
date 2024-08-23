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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.util.GlobalServerMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServernameFromSpyPortMappingTest {

  @BeforeEach
  void setup() {
    GlobalServerMap.clear();
  }

  @Test
  void testApply_whenPortExists_shouldReturnServerName() {
    // Setup a hostname element with localhost:1234
    RbelElement hostnameFacet =
        RbelHostnameFacet.buildRbelHostnameFacet(null, new RbelHostname("localhost", 1234));
    ServernameFromSpyPortMapping mapping = new ServernameFromSpyPortMapping();
    GlobalServerMap.addServerNameForPort(1234, "myTestServer");

    Optional<String> result = mapping.apply(hostnameFacet);

    assertThat(result).hasValue("myTestServer");
  }

  @Test
  void testApply_whenPortDoesNotExist_shouldReturnEmpty() {
    // Setup a hostname element with localhost:1234
    RbelElement hostnameFacet =
        RbelHostnameFacet.buildRbelHostnameFacet(null, new RbelHostname("localhost", 1234));
    ServernameFromSpyPortMapping mapping = new ServernameFromSpyPortMapping();

    Optional<String> result = mapping.apply(hostnameFacet);

    assertThat(result).isNotPresent();
  }

  @Test
  void testExtractPort() {
    // setup a rbel element that is not really a RbelHostnameFacet
    RbelElement hostnameFacet = new RbelElement(null, null);

    ServernameFromSpyPortMapping supplier = new ServernameFromSpyPortMapping();

    assertThatThrownBy(() -> supplier.apply(hostnameFacet))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("failed to extract port");
  }
}
