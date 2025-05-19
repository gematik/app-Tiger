/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.data.decorator;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.core.RbelHostnameFacet;
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
