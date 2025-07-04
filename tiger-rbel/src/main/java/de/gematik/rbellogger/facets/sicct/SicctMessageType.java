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
package de.gematik.rbellogger.facets.sicct;

import de.gematik.test.tiger.exceptions.GenericTigerException;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum SicctMessageType {
  // compare SICCT-specification, chapter 6.1.4.2

  C_COMMAND((byte) 0x6B),
  R_COMMAND((byte) 0x83),
  EVENT_MESSAGE((byte) 0x50);

  final byte value;

  public static SicctMessageType of(byte input) {
    return Stream.of(SicctMessageType.values())
        .filter(type -> type.value == input)
        .findFirst()
        .orElseThrow(() -> new UnknownSicctMessageTypeException(input));
  }

  private static class UnknownSicctMessageTypeException extends GenericTigerException {
    public UnknownSicctMessageTypeException(byte input) {
      super("Could not determine message type for " + input + "!");
    }
  }
}
