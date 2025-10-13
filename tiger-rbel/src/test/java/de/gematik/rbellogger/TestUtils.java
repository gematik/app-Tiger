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

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.util.RbelInternetAddress;
import de.gematik.rbellogger.util.RbelSocketAddress;
import de.gematik.test.tiger.exceptions.RbelHostnameFormatException;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.function.Function;
import lombok.val;
import org.apache.commons.io.FileUtils;

public class TestUtils {

  public static RbelElement readAndConvertCurlMessage(
      String fileName, Function<String, String>... messageMappers) throws IOException {
    String curlMessage = readCurlFromFileWithCorrectedLineBreaks(fileName);
    for (Function<String, String> mapper : messageMappers) {
      curlMessage = mapper.apply(curlMessage);
    }
    return RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);
  }

  public static String readCurlFromFileWithCorrectedLineBreaks(String fileName) throws IOException {
    return readCurlFromFileWithCorrectedLineBreaks(fileName, Charset.defaultCharset());
  }

  public static String readCurlFromFileWithCorrectedLineBreaks(String fileName, Charset charset)
      throws IOException {
    String fromFile =
        FileUtils.readFileToString(new File(fileName), charset).replaceAll("(?<!\\r)\\n", "\r\n");
    if (fromFile.lines().findFirst().filter(line -> line.contains("HTTP/")).isEmpty()) {
      return fromFile;
    }
    if (!fromFile.endsWith("\r\n")) {
      fromFile += "\r\n";
    }
    if (!fromFile.toLowerCase().contains("content-length")
        && !fromFile.toLowerCase().contains("transfer-encoding")) {
      val headerEndIndex = fromFile.indexOf("\r\n\r\n");
      val bodyLength = fromFile.substring(headerEndIndex + 4).getBytes(charset).length;
      if (bodyLength > 0) {
        fromFile =
            fromFile.substring(0, headerEndIndex)
                + "\r\n"
                + "Content-Length: "
                + bodyLength
                + "\r\n\r\n"
                + fromFile.substring(headerEndIndex + 4);
      }
    }
    return fromFile;
  }

  public static RbelSocketAddress localhostWithPort(int tcpPort) {
    try {
      return RbelSocketAddress.builder()
          .address(RbelInternetAddress.fromInetAddress(InetAddress.getLocalHost()))
          .port(tcpPort)
          .build();
    } catch (UnknownHostException e) {
      throw new RbelHostnameFormatException("Could not resolve localhost", e);
    }
  }
}
