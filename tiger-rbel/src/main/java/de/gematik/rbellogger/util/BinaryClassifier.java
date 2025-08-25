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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import lombok.SneakyThrows;

public class BinaryClassifier {

  private BinaryClassifier() {}

  private static final int BYTES_TO_CHECK = 100;

  public static boolean isBinary(byte[] data) {
    return isBinary(new ByteArrayInputStream(data));
  }

  @SneakyThrows
  public static boolean isBinary(InputStream data) {
    for (int readByte, pos = 0; pos < BYTES_TO_CHECK && (readByte = data.read()) >= 0; pos++) {
      // CR LF
      if (readByte == 0xA || readByte == 0xD) {
        continue;
      }
      if (readByte < 0x20) {
        return true;
      }
    }
    return false;
  }
}
