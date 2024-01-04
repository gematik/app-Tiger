/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

public class GlobalServerMap {

  private GlobalServerMap() {
    throw new IllegalStateException("GlobalServerMap class");
  }

  private static final Map<Long, String> PROCESS_ID_TO_BUNDLED_SERVER_NAME = new HashMap<>();
  private static final ConcurrentMap<Integer, Long> PORT_TO_PROCESS_ID = new ConcurrentHashMap<>();

  public static void mapPortToProcessIds(int key, long value) {
    PORT_TO_PROCESS_ID.putIfAbsent(key, value);
  }

  public static void updateGlobalServerMap(
      URL healthcheckUrl, AtomicReference<Process> processAtomicReference, String serverId) {
    mapPortToProcessIds(healthcheckUrl.getPort(), processAtomicReference.get().pid());
    PROCESS_ID_TO_BUNDLED_SERVER_NAME.put(processAtomicReference.get().pid(), serverId);
  }

  public static Map<Long, String> getProcessIdToBundledServerName() {
    return PROCESS_ID_TO_BUNDLED_SERVER_NAME;
  }

  public static ConcurrentMap<Integer, Long> getPortToProcessId() {
    return PORT_TO_PROCESS_ID;
  }
}
