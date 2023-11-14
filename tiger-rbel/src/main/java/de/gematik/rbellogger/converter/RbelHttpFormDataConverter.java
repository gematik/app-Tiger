/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelHttpFormDataFacet;
import de.gematik.rbellogger.data.facet.RbelHttpHeaderFacet;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;

public class RbelHttpFormDataConverter implements RbelConverterPlugin {

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConverter converter) {
    if (isBodyOfFormDataRequest(rbelElement)) {
      final RbelMultiMap formDataMap =
          Stream.of(rbelElement.getRawStringContent().split("&"))
              .map(param -> param.split("="))
              .filter(params -> params.length == 2)
              .map(
                  paramList ->
                      Pair.of(paramList[0], converter.convertElement(paramList[1], rbelElement)))
              .collect(RbelMultiMap.COLLECTOR);

      rbelElement.addFacet(RbelHttpFormDataFacet.builder().formDataMap(formDataMap).build());
    }
  }

  private boolean isBodyOfFormDataRequest(RbelElement rbelElement) {
    return Optional.ofNullable(rbelElement)
        .map(RbelElement::getParentNode)
        .filter(Objects::nonNull)
        .flatMap(el -> el.getFacet(RbelHttpMessageFacet.class))
        .map(RbelHttpMessageFacet::getHeader)
        .flatMap(el -> el.getFacet(RbelHttpHeaderFacet.class))
        .filter(
            header -> header.hasValueMatching("Content-Type", "application/x-www-form-urlencoded"))
        .isPresent();
  }
}
