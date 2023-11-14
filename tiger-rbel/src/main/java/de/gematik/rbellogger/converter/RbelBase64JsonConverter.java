/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelBase64Facet;
import de.gematik.rbellogger.data.facet.RbelJsonFacet;
import de.gematik.rbellogger.data.facet.RbelRootFacet;
import de.gematik.rbellogger.data.facet.RbelXmlFacet;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Objects;
import java.util.Optional;

public class RbelBase64JsonConverter extends RbelJsonConverter {

  @Override
  public void consumeElement(RbelElement rbel, RbelConverter context) {
    if (rbel.getRawStringContent().isEmpty()) {
      return;
    }
    safeConvertBase64Using(rbel.getRawStringContent(), Base64.getDecoder(), context, rbel)
        .or(
            () ->
                safeConvertBase64Using(
                    rbel.getRawStringContent(), Base64.getUrlDecoder(), context, rbel))
        .ifPresent(rbel::addFacet);
  }

  private Optional<RbelBase64Facet> safeConvertBase64Using(
      String input, Decoder decoder, RbelConverter context, RbelElement parentNode) {
    return Optional.ofNullable(input)
        .map(
            i -> {
              try {
                return decoder.decode(i);
              } catch (IllegalArgumentException e) {
                return null;
              }
            })
        .filter(Objects::nonNull)
        .map(data -> new RbelElement(data, parentNode))
        .stream()
        .peek(innerNode -> context.convertElement(innerNode))
        .filter(innerNode -> innerNode.hasFacet(RbelRootFacet.class))
        .filter(
            innerNode ->
                innerNode.getFacetOrFail(RbelRootFacet.class).getRootFacet()
                        instanceof RbelJsonFacet
                    || innerNode.getFacetOrFail(RbelRootFacet.class).getRootFacet()
                        instanceof RbelXmlFacet)
        .map(child -> new RbelBase64Facet(child))
        .findAny();
  }
}
