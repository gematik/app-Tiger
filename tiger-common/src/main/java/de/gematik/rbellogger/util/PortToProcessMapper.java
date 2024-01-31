/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
