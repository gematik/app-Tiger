/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@Builder(toBuilder = true)
public class RbelListFacet implements RbelFacet {

  private final List<RbelElement> childNodes;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    RbelMultiMap<RbelElement> result = new RbelMultiMap<>();
    AtomicInteger index = new AtomicInteger();
    childNodes.forEach(element -> result.put(String.valueOf(index.getAndIncrement()), element));
    return result;
  }

  public boolean isEmpty() {
    return childNodes.isEmpty();
  }
}
