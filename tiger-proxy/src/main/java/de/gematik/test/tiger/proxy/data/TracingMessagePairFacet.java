/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.data;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelFacet;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class TracingMessagePairFacet implements RbelFacet {

  private final RbelElement response;
  private final RbelElement request;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<>();
  }

  public Optional<RbelElement> getOtherMessage(RbelElement thisMessage) {
    if (thisMessage.equals(request)) {
      return Optional.of(response);
    } else if (thisMessage.equals(response)) {
      return Optional.of(request);
    } else {
      return Optional.empty();
    }
  }
}
