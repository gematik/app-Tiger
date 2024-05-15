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
public class RbelHttpRequestFacet implements RbelFacet {

  private final RbelElement method;
  private final RbelElement path;
  private final RbelElement response;

  @Builder(toBuilder = true)
  public RbelHttpRequestFacet(RbelElement method, RbelElement path, RbelElement response) {
    this.method = method;
    this.path = path;
    this.response = response;
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>().with("method", method).with("path", path);
  }

  public String getPathAsString() {
    return path.getRawStringContent();
  }

  @Override
  public boolean shouldExpectReplyMessage() {
    return true;
  }

  public static void updateResponseOfRequestFacet(RbelElement request, RbelElement response) {
    request.addOrReplaceFacet(
        request
            .getFacet(RbelHttpRequestFacet.class)
            .map(RbelHttpRequestFacet::toBuilder)
            .orElse(RbelHttpRequestFacet.builder())
            .response(response)
            .build());
  }
}
