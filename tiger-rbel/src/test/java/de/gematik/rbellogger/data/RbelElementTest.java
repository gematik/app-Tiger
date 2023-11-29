/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data;

import static de.gematik.rbellogger.TestUtils.readAndConvertCurlMessage;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.facet.*;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("unchecked")
class RbelElementTest {
  private static final RbelElement msg;

  static {
    try {
      msg = readAndConvertCurlMessage("src/test/resources/sampleMessages/xmlMessage.curl");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void renameElementViaBuilder_UuidShouldChange() {
    RbelElement originalElement = new RbelElement("fo".getBytes(), null);
    RbelElement renamedElement = originalElement.toBuilder().uuid("another uuid").build();

    assertThat(originalElement.getUuid()).isNotEqualTo(renamedElement.getUuid());
  }

  @Test
  void duplicatedElementViaBuilder_UuidShouldNotChange() {
    RbelElement originalElement = new RbelElement("fo".getBytes(), null);
    RbelElement renamedElement = originalElement.toBuilder().build();

    assertThat(originalElement.getUuid()).isEqualTo(renamedElement.getUuid());
  }

  private static Stream<Arguments> findAncestorWithFacetExamples() {
    return Stream.of(
        Arguments.of(
            "$..jwks_uri.content.basicPath",
            RbelValueFacet.class,
            Optional.of("$..jwks_uri.content")),
        Arguments.of(
            "$..jwks_uri.content", RbelRootFacet.class, Optional.of("$..jwtTag.text.body")),
        Arguments.of("$..jwks_uri.content", RbelXmlFacet.class, Optional.of("$..jwtTag")),
        Arguments.of("$..serialnumber", RbelAsn1Facet.class, Optional.of("$..x5c.0.content")),
        Arguments.of("$..serialnumber", RbelJwtFacet.class, Optional.of("$..jwtTag.text")),
        Arguments.of("$..serialnumber", RbelVauEpaFacet.class, Optional.empty()),
        Arguments.of("$..serialnumber", RbelHttpHeaderFacet.class, Optional.empty()));
  }

  @ParameterizedTest
  @MethodSource("findAncestorWithFacetExamples")
  void testFindAncestorWithMatchingFacet_happyPath(
      String baseElement, Class<RbelFacet> facet, Optional<String> targetElement) {
    final Optional<RbelElement> ancestorElement =
        msg.findElement(baseElement).get().findAncestorWithFacet(facet);
    final Optional<RbelElement> expected = targetElement.flatMap(msg::findElement);

    assertThat(ancestorElement).isEqualTo(expected);
  }
}
