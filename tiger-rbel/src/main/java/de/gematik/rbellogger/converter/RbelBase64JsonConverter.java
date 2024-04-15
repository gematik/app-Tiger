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
import java.util.Optional;

public class RbelBase64JsonConverter extends RbelJsonConverter {

  @Override
  public void consumeElement(RbelElement rbel, RbelConverter context) {
    if (rbel.getRawContent().length == 0) {
      return;
    }
    safeConvertBase64Using(rbel.getRawContent(), Base64.getDecoder(), context, rbel)
        .or(
            () ->
                safeConvertBase64Using(rbel.getRawContent(), Base64.getUrlDecoder(), context, rbel))
        .ifPresent(rbel::addFacet);
  }

  private Optional<RbelBase64Facet> safeConvertBase64Using(
      byte[] input, Decoder decoder, RbelConverter context, RbelElement parentNode) {
    final Optional<RbelElement> rawElement =
        Optional.ofNullable(input)
            .map(
                i -> {
                  try {
                    return decoder.decode(i);
                  } catch (IllegalArgumentException e) {
                    return null;
                  }
                })
            .map(data -> new RbelElement(data, parentNode));
    if (rawElement.isEmpty()) {
      return Optional.empty();
    }
    context.convertElement(rawElement.get());
    if (rawElement.get().hasFacet(RbelRootFacet.class)) {
      final RbelRootFacet<?> rootFacet = rawElement.get().getFacetOrFail(RbelRootFacet.class);
      if (rootFacet.getRootFacet() instanceof RbelJsonFacet
          || rootFacet.getRootFacet() instanceof RbelXmlFacet) {
        return Optional.of(new RbelBase64Facet(rawElement.get()));
      }
    }
    return Optional.empty();
  }
}
