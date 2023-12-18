/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data;

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.facet.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RbelElementAssertionTest {

  RbelElement sampleElement;

  public RbelElementAssertionTest() throws IOException {
    this.sampleElement =
        RbelLogger.build()
            .getRbelConverter()
            .convertElement(
                Files.readAllBytes(Path.of("src/test/resources/jsonMessage.curl")), null);
    ;
  }

  @Test
  void testBasicFunctions() {
    assertThat(sampleElement)
        .extractChildWithPath("$.header.Version")
        .hasStringContentEqualTo("9.0.0")
        .asString()
        .isEqualTo("9.0.0");
  }

  @Test
  void testFacetCheck() {
    assertThat(sampleElement)
        .extractChildWithPath("$.body.keys")
        .hasFacet(RbelJsonFacet.class)
        .hasFacet(RbelListFacet.class)
        .doesNotHaveFacet(RbelAsn1Facet.class);
    assertThatThrownBy(
            () ->
                assertThat(sampleElement)
                    .extractChildWithPath("$.body.keys")
                    .hasFacet(RbelAsn1Facet.class))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(
            "Expecting element to have facet of type RbelAsn1Facet, but only found facets");
    assertThatThrownBy(() -> assertThat(sampleElement).doesNotHaveFacet(RbelHttpMessageFacet.class))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(
            "Expecting element to have NOT facet of type RbelHttpMessageFacet, but it was found"
                + " along with ");
  }

  @Test
  void testNullContent() {
    assertThatThrownBy(
            () ->
                assertThat(sampleElement).extractChildWithPath("$.header.Version").hasNullContent())
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Expecting null content, but found 9.0.0");
  }

  @Test
  void testPathWithNoMatches() {
    assertThatThrownBy(() -> assertThat(sampleElement).extractChildWithPath("$.blubblab"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Expected rbelPath $.blubblab to find member, but did not in tree ");
  }

  @Test
  void testPathWithMultipleMatches() {
    assertThatThrownBy(() -> assertThat(sampleElement).extractChildWithPath("$..*"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Expected rbelPath $..* to find one member, but did return ");
  }

  @Test
  void testHasValueEqualTo() {
    assertThat(sampleElement).extractChildWithPath("$..0.kid.content").hasValueEqualTo("idpSig");
  }
}
