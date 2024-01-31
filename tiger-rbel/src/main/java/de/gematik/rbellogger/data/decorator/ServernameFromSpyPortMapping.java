/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.decorator;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.util.GlobalServerMap;
import java.util.Optional;
import java.util.function.Function;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class ServernameFromSpyPortMapping implements Function<RbelElement, Optional<String>> {

  @Override
  public Optional<String> apply(RbelElement element) {
    return findBundledServerNameForHostnameFacet(element);
  }

  private Optional<String> findBundledServerNameForHostnameFacet(RbelElement hostNameElement) {
    Integer port =
        extractPort(hostNameElement)
            .orElseThrow(() -> new IllegalStateException("failed to extract port"));
    return GlobalServerMap.getServerNameForPort(port);
  }

  private Optional<Integer> extractPort(RbelElement hostNameElement) {
    return hostNameElement
        .getFacet(RbelHostnameFacet.class)
        .map(RbelHostnameFacet::toRbelHostname)
        .map(RbelHostname::getPort);
  }
}
