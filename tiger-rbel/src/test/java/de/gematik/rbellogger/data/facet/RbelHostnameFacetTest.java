/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import org.junit.jupiter.api.Test;

class RbelHostnameFacetTest {

  @Test
  void generateRbelHostnameFacet() {
    RbelElement element =
        RbelHostnameFacet.buildRbelHostnameFacet(
            new RbelElement(null, null),
            (RbelHostname) RbelHostname.generateFromUrl("https://foo:8080").get());
    assertThat(element.hasFacet(RbelHostnameFacet.class)).isTrue();
    assertThat(element.getFacetOrFail(RbelHostnameFacet.class).getDomain().getRawStringContent())
        .isEqualTo("foo");
    assertThat(element.getFacetOrFail(RbelHostnameFacet.class).getPort().getRawStringContent())
        .isEqualTo("8080");
  }

  @Test
  void generateRbelHostnameFacet_withoutPortAndHttps() {
    RbelElement element =
        RbelHostnameFacet.buildRbelHostnameFacet(
            new RbelElement(null, null),
            (RbelHostname) RbelHostname.generateFromUrl("https://foo").get());
    assertThat(element.hasFacet(RbelHostnameFacet.class)).isTrue();
    assertThat(element.getFacetOrFail(RbelHostnameFacet.class).getDomain().getRawStringContent())
        .isEqualTo("foo");
    assertThat(element.getFacetOrFail(RbelHostnameFacet.class).getPort().getRawStringContent())
        .isEqualTo("443");
  }

  @Test
  void generateRbelHostnameFacet_withoutPortAndHttp() {
    RbelElement element =
        RbelHostnameFacet.buildRbelHostnameFacet(
            new RbelElement(null, null),
            (RbelHostname) RbelHostname.generateFromUrl("http://foo").get());
    assertThat(element.hasFacet(RbelHostnameFacet.class)).isTrue();
    assertThat(element.getFacetOrFail(RbelHostnameFacet.class).getDomain().getRawStringContent())
        .isEqualTo("foo");
    assertThat(element.getFacetOrFail(RbelHostnameFacet.class).getPort().getRawStringContent())
        .isEqualTo("80");
  }
}
