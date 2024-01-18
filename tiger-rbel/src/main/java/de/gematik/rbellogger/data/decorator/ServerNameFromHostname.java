/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.decorator;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import java.util.Optional;
import java.util.function.Function;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class ServerNameFromHostname implements Function<RbelElement, Optional<String>> {

  private String checkIfLocalProxy(String realAddress) {
    if (realAddress.startsWith("127.0.0.1")) {
      return "local client";
    }
    return realAddress;
  }

  private Optional<String> extractHostname(RbelElement hostNameElement) {
    return hostNameElement
        .getFacet(RbelHostnameFacet.class)
        .map(RbelHostnameFacet::toRbelHostname)
        .map(RbelHostname::getHostname);
  }

  @Override
  public Optional<String> apply(RbelElement element) {
    return extractHostname(element).map(this::checkIfLocalProxy);
  }
}
