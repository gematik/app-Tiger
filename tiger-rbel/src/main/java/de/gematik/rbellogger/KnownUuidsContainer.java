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
package de.gematik.rbellogger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class KnownUuidsContainer {
  private final Object monitor;
  private final Map<String, MessageUuidState> knownMessageUuids = new HashMap<>();
  @Setter private Consumer<List<String>> removedMessageUuidsHandler;

  public KnownUuidsContainer(Object monitor) {
    this.monitor = monitor;
  }

  public boolean isAlreadyConverted(String msgUuid) {
    synchronized (monitor) {
      return knownMessageUuids.get(msgUuid) == MessageUuidState.CONVERTED;
    }
  }

  public boolean add(String messageUuid) {
    if (StringUtils.isBlank(messageUuid)) {
      return true;
    }
    synchronized (monitor) {
      if (knownMessageUuids.containsKey(messageUuid)) {
        return false;
      } else {
        knownMessageUuids.put(messageUuid, MessageUuidState.TO_BE_CONVERTED);
        return true;
      }
    }
  }

  public void markAsConverted(String uuid) {
    synchronized (monitor) {
      knownMessageUuids.put(uuid, MessageUuidState.CONVERTED);
    }
  }

  public void clear() {
    List<String> removedUuids = null;
    synchronized (monitor) {
      if (removedMessageUuidsHandler != null) {
        removedUuids = List.copyOf(knownMessageUuids.keySet());
      }
      knownMessageUuids.clear();
    }
    if (removedMessageUuidsHandler != null && removedUuids != null && !removedUuids.isEmpty()) {
      log.trace("Clearing known message UUIDs: {}", removedUuids);
      removedMessageUuidsHandler.accept(removedUuids);
    }
  }

  public void remove(String uuid) {
    synchronized (monitor) {
      knownMessageUuids.remove(uuid);
    }
    if (removedMessageUuidsHandler != null) {
      removedMessageUuidsHandler.accept(List.of(uuid));
    }
  }

  private enum MessageUuidState {
    TO_BE_CONVERTED,
    CONVERTED
  }
}
