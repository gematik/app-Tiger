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

package de.gematik.rbellogger.util;

import static de.gematik.rbellogger.util.GlobalServerMap.mapPortToProcessIds;

import java.util.List;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import oshi.SystemInfo;
import oshi.software.os.InternetProtocolStats;
import oshi.software.os.OperatingSystem;

@Slf4j
public class PortToProcessMapper {

  private PortToProcessMapper() {
    throw new IllegalStateException("PortToProcessMapper class");
  }

  public static ConcurrentMap<Integer, Long> getProcessIdsForPort(int port) {
    getConnectionsToAndFromPort(port).forEach(PortToProcessMapper::fillMapWithValues);
    return GlobalServerMap.getPortToProcessId();
  }

  private static void fillMapWithValues(InternetProtocolStats.IPConnection connection) {
    if (GlobalServerMap.getProcessIdToBundledServerName()
        .containsKey((long) connection.getowningProcessId())) {
      mapPortToProcessIds(connection.getLocalPort(), connection.getowningProcessId());
      mapPortToProcessIds(connection.getForeignPort(), connection.getowningProcessId());
    }
  }

  public static List<InternetProtocolStats.IPConnection> getConnectionsToAndFromPort(int port) {
    SystemInfo si = new SystemInfo();
    OperatingSystem os = si.getOperatingSystem();
    InternetProtocolStats ipStats = os.getInternetProtocolStats();

    return ipStats.getConnections().stream()
        .filter(c -> c.getLocalPort() == port || c.getForeignPort() == port)
        .toList();
  }
}
