/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
