/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */
package de.gematik.rbellogger.data.decorator;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
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
