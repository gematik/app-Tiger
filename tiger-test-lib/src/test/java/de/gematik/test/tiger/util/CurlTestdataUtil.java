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
package de.gematik.test.tiger.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import org.apache.commons.io.FileUtils;

public class CurlTestdataUtil {

  public static String readCurlFromFileWithCorrectedLineBreaks(String fileName) {
    try {
      // Convert ALL line endings (headers and body) to \r\n
      String fromFile =
          FileUtils.readFileToString(new File(fileName), Charset.defaultCharset())
              .replaceAll("(?<!\\r)\\n", "\r\n");
      boolean addedTrailingCRLF = false;
      if (!fromFile.endsWith("\r\n")) {
        fromFile += "\r\n";
        addedTrailingCRLF = true;
      }
      // Strip any existing Content-Length so we can recalculate it correctly
      fromFile = fromFile.replaceAll("(?i)\r\nContent-Length: [0-9]+", "");
      int headerEndIndex = fromFile.indexOf("\r\n\r\n");
      if (headerEndIndex >= 0) {
        String body = fromFile.substring(headerEndIndex + 4);
        // The artificially added trailing \r\n is HTTP message framing, not body content
        String bodyForLength =
            (addedTrailingCRLF && body.endsWith("\r\n"))
                ? body.substring(0, body.length() - 2)
                : body;
        int bodyLength = bodyForLength.getBytes(Charset.defaultCharset()).length;
        if (bodyLength > 0) {
          fromFile =
              fromFile.substring(0, headerEndIndex)
                  + "\r\nContent-Length: "
                  + bodyLength
                  + "\r\n\r\n"
                  + body;
        }
      }
      return fromFile;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
