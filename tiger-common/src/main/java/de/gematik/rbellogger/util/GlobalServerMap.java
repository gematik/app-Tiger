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
