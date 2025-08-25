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

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

public class ByteArrayUtils {
  private static class NonCopyingByteArrayOutputStream extends ByteArrayOutputStream {
    public synchronized byte[] getBytes() {
      return buf;
    }
  }

  public interface OutputStreamWriter {
    void write(OutputStream outputStream) throws Exception;
  }

  public static byte[] getBytesFrom(OutputStreamWriter writer) throws Exception {
    try (var out = new NonCopyingByteArrayOutputStream()) {
      writer.write(out);
      return out.getBytes();
    }
  }
}
