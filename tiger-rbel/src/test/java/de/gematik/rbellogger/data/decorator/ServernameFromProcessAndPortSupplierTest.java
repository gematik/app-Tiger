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
package de.gematik.rbellogger.data.decorator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelHostnameFacet;
import de.gematik.rbellogger.util.GlobalServerMap;
import de.gematik.rbellogger.util.RbelSocketAddress;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServernameFromProcessAndPortSupplierTest {

  @BeforeEach
  void setup() {
    GlobalServerMap.clear();
  }

  @SneakyThrows
  @Test
  void testApply_whenProcessIdExists_shouldReturnServerName() {
    // setup a hostname element with localhost:1234
    RbelElement hostnameFacet =
        RbelHostnameFacet.buildRbelHostnameFacet(null, RbelSocketAddress.create("localhost", 1234));

    // Configure GlobalServerMap to return a mock process id
    GlobalServerMap.updateGlobalServerMap(1234, 51234, "someTestServer");
    ServernameFromProcessAndPortSupplier supplier = new ServernameFromProcessAndPortSupplier();

    // apply supplier
    Optional<String> result = supplier.apply(hostnameFacet);

    assertThat(result).hasValue("someTestServer");
  }

  @SneakyThrows
  @Test
  void testApply_whenProcessIdDoesNotExist_shouldReturnEmpty() {
    // setup a hostname element with localhost:1234
    RbelElement hostnameFacet =
        RbelHostnameFacet.buildRbelHostnameFacet(null, RbelSocketAddress.create("localhost", 1234));

    // Do not add anything to the global server map
    ServernameFromProcessAndPortSupplier supplier = new ServernameFromProcessAndPortSupplier();

    // apply supplier
    Optional<String> result = supplier.apply(hostnameFacet);

    assertThat(result).isNotPresent();
  }

  @SneakyThrows
  @Test
  void testApply_whenPortExtractionFails_shouldThrowException() {
    // setup a rbel element that is not really a RbelHostnameFacet
    RbelElement hostnameFacet = new RbelElement();

    ServernameFromProcessAndPortSupplier supplier = new ServernameFromProcessAndPortSupplier();

    assertThatThrownBy(() -> supplier.apply(hostnameFacet))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("failed to extract port");
  }
}
