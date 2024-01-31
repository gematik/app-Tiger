/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.decorator;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.util.GlobalServerMap;
import de.gematik.rbellogger.util.PortToProcessMapper;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class ServernameFromProcessAndPortSupplier
    implements Function<RbelElement, Optional<String>> {
  @Override
  public Optional<String> apply(RbelElement element) {
    return findBundledServerNameForHostnameFacet(element);
  }

  private Optional<String> findBundledServerNameForHostnameFacet(RbelElement hostNameElement) {
    Integer port =
        extractPort(hostNameElement)
            .orElseThrow(() -> new IllegalStateException("failed to extract port"));

    Long processId = GlobalServerMap.getPortToProcessId().get(port);

    if (processId == null) {
      ConcurrentMap<Integer, Long> updatedMapWithPortsAndProcessIds =
          PortToProcessMapper.getProcessIdsForPort(port);
      processId = updatedMapWithPortsAndProcessIds.get(port);
    }

    if (processId != null
        && GlobalServerMap.getProcessIdToBundledServerName().containsKey(processId)) {
      return Optional.of(GlobalServerMap.getProcessIdToBundledServerName().get(processId));
    }
    return Optional.empty();
  }

  private Optional<Integer> extractPort(RbelElement hostNameElement) {
    return hostNameElement
        .getFacet(RbelHostnameFacet.class)
        .map(RbelHostnameFacet::toRbelHostname)
        .map(RbelHostname::getPort);
  }
}
