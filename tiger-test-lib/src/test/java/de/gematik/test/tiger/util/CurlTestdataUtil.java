/*
 *
 * Copyright 2025 gematik GmbH
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
 */
package de.gematik.test.tiger.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import lombok.val;
import org.apache.commons.io.FileUtils;

public class CurlTestdataUtil {

  public static String readCurlFromFileWithCorrectedLineBreaks(String fileName) {
    try {
      String fromFile = FileUtils.readFileToString(new File(fileName), Charset.defaultCharset());
      val messageParts = fromFile.split("(\r\n\r\n)|(\n\n)|(\r\r)", 2);
      messageParts[0] = messageParts[0].replaceAll("(?<!\\r)\\n", "\r\n");
      if (!messageParts[1].endsWith("\r\n")) {
        messageParts[1] += "\r\n";
      }
      if (!messageParts[0].contains("\r\nContent-Length: ")) {
        fromFile =
            messageParts[0]
                + "\r\nContent-Length: "
                + (messageParts[1].length() + 2)
                + "\r\n\r\n"
                + messageParts[1];
      }
      return fromFile;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
