/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.rbellogger.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.assertj.core.util.VisibleForTesting;

public class GlobalServerMap {

  private GlobalServerMap() {
    throw new IllegalStateException("GlobalServerMap class");
  }

  private static final Map<Long, String> PROCESS_ID_TO_BUNDLED_SERVER_NAME = new HashMap<>();
  private static final ConcurrentMap<Integer, Long> PORT_TO_PROCESS_ID = new ConcurrentHashMap<>();

  private static final ConcurrentMap<Integer, String> PORT_TO_LOCAL_SERVER_TYPES_NAMES =
      new ConcurrentHashMap<>();

  public static void mapPortToProcessIds(int key, long value) {
    PORT_TO_PROCESS_ID.putIfAbsent(key, value);
  }

  public static void updateGlobalServerMap(int portNumber, long processId, String serverId) {
    mapPortToProcessIds(portNumber, processId);
    PROCESS_ID_TO_BUNDLED_SERVER_NAME.put(processId, serverId);
  }

  public static Map<Long, String> getProcessIdToBundledServerName() {
    return PROCESS_ID_TO_BUNDLED_SERVER_NAME;
  }

  public static ConcurrentMap<Integer, Long> getPortToProcessId() {
    return PORT_TO_PROCESS_ID;
  }

  public static Optional<String> getServerNameForPort(int port) {
    return Optional.ofNullable(PORT_TO_LOCAL_SERVER_TYPES_NAMES.get(port));
  }

  public static void addServerNameForPort(int port, String serverName) {
    PORT_TO_LOCAL_SERVER_TYPES_NAMES.put(port, serverName);
  }

  @VisibleForTesting
  public static void clear() {
    PROCESS_ID_TO_BUNDLED_SERVER_NAME.clear();
    PORT_TO_PROCESS_ID.clear();
    PORT_TO_LOCAL_SERVER_TYPES_NAMES.clear();
  }
}
