/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.decorator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.util.GlobalServerMap;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class ServernameFromProcessAndPortSupplierTest {

  @SneakyThrows
  @Test
  void testApply_whenProcessIdExists_shouldReturnServerName() {
    // setup a hostname element with localhost:1234
    RbelElement messageWithTcpFacet =
        RbelHostnameFacet.buildRbelHostnameFacet(null, new RbelHostname("localhost", 1234));

    // Configure GlobalServerMap to return a mock process id
    GlobalServerMap.updateGlobalServerMap(1234, 51234, "someTestServer");
    ServernameFromProcessAndPortSupplier supplier = new ServernameFromProcessAndPortSupplier();

    // apply supplier
    Optional<String> result = supplier.apply(messageWithTcpFacet);

    assertThat(result).hasValue("someTestServer");
  }

  @SneakyThrows
  @Test
  void testApply_whenProcessIdDoesNotExist_shouldReturnEmpty() {
    // setup a hostname element with localhost:1234
    RbelElement messageWithTcpFacet =
        RbelHostnameFacet.buildRbelHostnameFacet(null, new RbelHostname("localhost", 1234));

    // Do not add anything to the global server map
    ServernameFromProcessAndPortSupplier supplier = new ServernameFromProcessAndPortSupplier();

    // apply supplier
    Optional<String> result = supplier.apply(messageWithTcpFacet);

    assertThat(result).isNotPresent();
  }

  @SneakyThrows
  @Test
  void testApply_whenPortExtractionFails_shouldThrowException() {
    // setup a rbel element that is not really a RbelHostnameFacet
    RbelElement messageWithTcpFacet = new RbelElement(null, null);

    // Configure GlobalServerMap to return a mock process id
    GlobalServerMap.updateGlobalServerMap(1234, 51234, "someTestServer");
    ServernameFromProcessAndPortSupplier supplier = new ServernameFromProcessAndPortSupplier();

    assertThatThrownBy(() -> supplier.apply(messageWithTcpFacet))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("failed to extract port");
  }
}
