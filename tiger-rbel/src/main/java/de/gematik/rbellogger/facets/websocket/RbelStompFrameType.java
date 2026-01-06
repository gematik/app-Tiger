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
package de.gematik.rbellogger.facets.websocket;

import de.gematik.rbellogger.util.RbelContent;

public enum RbelStompFrameType {
  SEND,
  SUBSCRIBE,
  UNSUBSCRIBE,
  BEGIN,
  COMMIT,
  ABORT,
  ACK,
  NACK,
  DISCONNECT,
  MESSAGE,
  ERROR,
  CONNECT,
  CONNECTED;

  private final byte[] commandString;

  RbelStompFrameType() {
    this.commandString = name().getBytes();
  }

  public static boolean isCommand(RbelContent command) {
    for (RbelStompFrameType frameType : RbelStompFrameType.values()) {
      if (command.size() == frameType.commandString.length
          && command.startsWith(frameType.commandString)) {
        return true;
      }
    }
    return false;
  }
}
