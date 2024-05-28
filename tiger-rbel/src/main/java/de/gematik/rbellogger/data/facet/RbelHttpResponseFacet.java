/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class RbelHttpResponseFacet implements RbelFacet {

  private final RbelElement responseCode;
  private final RbelElement reasonPhrase;
  private final RbelElement request;

  @Builder(toBuilder = true)
  public RbelHttpResponseFacet(
      RbelElement responseCode, RbelElement reasonPhrase, RbelElement request) {
    this.responseCode = responseCode;
    this.reasonPhrase = reasonPhrase;
    this.request = request;
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .with("responseCode", responseCode)
        .with("reasonPhrase", reasonPhrase);
  }

  public static void updateRequestOfResponseFacet(RbelElement response, RbelElement request) {
    response
        .getFacet(RbelHttpResponseFacet.class)
        .map(RbelHttpResponseFacet::toBuilder)
        .map(builder -> builder.request(request).build())
        .ifPresent(response::addOrReplaceFacet);
  }
}
