/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.data.decorator;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.util.GlobalServerMap;
import de.gematik.rbellogger.util.PortToProcessMapper;
import de.gematik.test.tiger.common.config.TigerConfigurationKeys;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Modifies a given RbelElement to include the bundled sender and receiver server names. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AddBundledServerNamesModifier implements MessageMetadataModifier {

  /**
   * Returns the modifier based on the configuration flag.
   *
   * @return a MessageMetadataModifier
   */
  public static MessageMetadataModifier createModifier() {
    if (Boolean.TRUE.equals(
        TigerConfigurationKeys.ExperimentalFeatures.TRAFFIC_VISUALIZATION_ACTIVE
            .getValueOrDefault())) {
      return new AddBundledServerNamesModifier();
    } else {
      return new DoesNothingModifier();
    }
  }

  @Override
  public void modifyMetadata(RbelElement message) {
    addBundledServerNamesToMessage(message);
  }

  private void addBundledServerNamesToMessage(RbelElement message) {
    message
        .getFacet(RbelTcpIpMessageFacet.class)
        .ifPresent(
            rbelTcpIpMessageFacet -> {
              setBundledServerNameBasedOnServerType(rbelTcpIpMessageFacet.getSender());
              setBundledServerNameBasedOnServerType(rbelTcpIpMessageFacet.getReceiver());
            });
  }

  private void setBundledServerNameBasedOnServerType(RbelElement serverType) {
    serverType
        .getFacet(RbelHostnameFacet.class)
        .ifPresent(
            rbelHostnameFacet ->
                rbelHostnameFacet.setBundledServerName(
                    findBundledServerNameForHostnameFacet(rbelHostnameFacet, serverType)));
  }

  private Optional<RbelElement> findBundledServerNameForHostnameFacet(
      RbelHostnameFacet rbelHostnameFacet, RbelElement serverType) {
    Optional<Integer> optionalPort = rbelHostnameFacet.getPort().seekValue(Integer.class);
    if (optionalPort.isPresent()) {
      Integer port = optionalPort.get();
      Long processId = GlobalServerMap.getPortToProcessId().get(port);

      if (processId == null) {
        ConcurrentMap<Integer, Long> updatedMapWithPortsAndProcessIds =
            PortToProcessMapper.runPortMappingCommand(port);
        processId = updatedMapWithPortsAndProcessIds.get(port);
      }

      if (GlobalServerMap.getProcessIdToBundledServerName().containsKey(processId)
          && processId != null) {
        String bundledServerName = GlobalServerMap.getProcessIdToBundledServerName().get(processId);
        return Optional.of(RbelElement.wrap(serverType, bundledServerName));
      }
    }
    return Optional.empty();
  }
}
