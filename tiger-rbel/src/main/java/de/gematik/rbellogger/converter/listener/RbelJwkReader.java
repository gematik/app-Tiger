/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter.listener;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.converter.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelJsonFacet;
import de.gematik.rbellogger.data.facet.RbelListFacet;
import de.gematik.rbellogger.data.facet.RbelNoteFacet;
import de.gematik.rbellogger.data.facet.RbelNoteFacet.NoteStyling;
import de.gematik.rbellogger.key.RbelKey;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKey.Factory;
import org.jose4j.lang.JoseException;

public class RbelJwkReader implements RbelConverterPlugin {

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConverter converter) {
    final List<RbelElement> keysList =
        Optional.of(rbelElement)
            .filter(element -> element.hasFacet(RbelJsonFacet.class))
            .map(element -> element.getAll("keys"))
            .stream()
            .flatMap(List::stream)
            .filter(el -> el.hasFacet(RbelJsonFacet.class))
            .filter(el -> el.hasFacet(RbelListFacet.class))
            .map(el -> el.getFacet(RbelListFacet.class))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(RbelListFacet::getChildNodes)
            .flatMap(List::stream)
            .collect(Collectors.toList());

    for (RbelElement keyElement : keysList) {
      tryToConvertKeyAndAddToKeyManager(keyElement, converter);
    }
  }

  private void tryToConvertKeyAndAddToKeyManager(RbelElement keyElement, RbelConverter converter) {
    try {
      final JsonWebKey jwk = Factory.newJwk(keyElement.getRawStringContent());
      converter
          .getRbelKeyManager()
          .addKey(
              RbelKey.builder()
                  .key(jwk.getKey())
                  .keyName(jwk.getKeyId())
                  .precedence(RbelKey.PRECEDENCE_JWK_VALUE)
                  .build());
    } catch (RuntimeException | JoseException e) {
      keyElement.addFacet(
          RbelNoteFacet.builder()
              .value("Unable to parse key: " + e.getMessage())
              .style(NoteStyling.WARN)
              .build());
    }
  }
}
