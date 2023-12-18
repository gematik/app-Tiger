/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RbelHostnameFacetTest {

  @ParameterizedTest
  @CsvSource({"https://foo,foo,443", "http://foo,foo,80", "https://foo:8080,foo,8080"})
  void generateRbelHostnameFacet(String url, String hostname, String port) {
    RbelElement element =
        RbelHostnameFacet.buildRbelHostnameFacet(
            new RbelElement(null, null), (RbelHostname) RbelHostname.generateFromUrl(url).get());
    assertThat(element.hasFacet(RbelHostnameFacet.class)).isTrue();
    assertThat(element.getFacetOrFail(RbelHostnameFacet.class).getDomain().getRawStringContent())
        .isEqualTo(hostname);
    assertThat(element.getFacetOrFail(RbelHostnameFacet.class).getPort().getRawStringContent())
        .isEqualTo(port);
  }
}
