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
package de.gematik.test.tiger.lib.reports;

import de.gematik.test.tiger.lib.TigerLibraryException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RestAssuredLogToCurlCommandParser {

  public static List<String> convertRestAssuredLogToCurlCalls(final String raLog) {
    List<String> requestLogs = new ArrayList<>();

    final String[] lines = raLog.split("\\n");
    StringBuilder sb = new StringBuilder();
    for (String line : lines) {
      if (line.trim().startsWith("Request method:") && !sb.isEmpty()) {
        requestLogs.add(sb.toString());
        sb.setLength(0);
      }
      sb.append(line).append("\n");
    }
    if (!sb.isEmpty()) {
      requestLogs.add(sb.toString());
    }
    return requestLogs;
  }

  public static String parseCurlCommandFromRestAssuredLog(final String rALogDetails) {
    final String[] lines = rALogDetails.split("\\n");

    final Optional<String> uri = getOptionalValueFromLogLine(lines, "Request URI:");
    final Optional<String> method = getOptionalValueFromLogLine(lines, "Request method:");

    final StringBuilder curlCmd = new StringBuilder("curl -v ");
    if (uri.isPresent() && method.isPresent()) {
      parseHeaders(lines, curlCmd);
      parseMethodAndBody(method, curlCmd, uri, lines);
    } else {
      curlCmd.append("Unable to parse log data");
    }
    return curlCmd.toString();
  }

  private static void parseMethodAndBody(
      Optional<String> method, StringBuilder curlCmd, Optional<String> uri, String[] lines) {
    if (method.isEmpty()) {
      throw new TigerLibraryException("Unable to parse request with EMPTY http method!");
    }
    if (uri.isEmpty()) {
      throw new TigerLibraryException("Unable to parse request with EMPTY uri!");
    }
    switch (method.get()) {
      case "GET" -> curlCmd.append("\" -X GET \"").append(uri.get()).append("\" ");
      case "POST" -> {
        // Add form params
        final StringBuilder paramsStr = new StringBuilder();
        if (createCurlParamString(paramsStr, getValuesForBlock(lines, "Form params"))) {
          curlCmd.append(" ").append(paramsStr).append("\" ");
        }
        curlCmd.append("\" -X POST \"").append(uri.get()).append("\" ");
      }
      case "DELETE" -> curlCmd.append("\" -X DELETE \"").append(uri.get()).append("\" ");
      case "PUT" -> curlCmd
          .append("\" -X PUT -d '")
          .append(createCurlBodyString(getValuesForBlock(lines, "Body")))
          .append("' \"")
          .append(uri.get())
          .append("\" ");
      default -> throw new TigerLibraryException(
          "Unable to parse http method '" + method.get() + "'");
    }
  }

  private static void parseHeaders(String[] lines, StringBuilder curlCmd) {
    // Add headers
    final List<String> headers = getValuesForBlock(lines, "Headers");
    boolean isFirstHeader = true;
    for (final String header : headers) {
      if (!header.isEmpty()) {
        if (header.contains("=")) {
          if (isFirstHeader) {
            curlCmd.append("-H \"");
            isFirstHeader = false;
          } else {
            curlCmd.append("\" -H \"");
          }
          final int equal = header.indexOf("=");
          curlCmd.append(header, 0, equal).append(": ").append(header.substring(equal + 1));
        } else {
          curlCmd.append(header, 0, header.length());
        }
      }
    }
  }

  private static Optional<String> getOptionalValueFromLogLine(
      final String[] lines, final String prefix) {
    return Stream.of(lines)
        .filter(l -> l.trim().startsWith(prefix))
        .map(line -> line.substring(prefix.length()).trim())
        .findFirst();
  }

  private static boolean createCurlParamString(
      final StringBuilder paramsStr, final List<String> params) {
    boolean first = true;
    for (final String param : params) {
      final int equal = param.indexOf("=");
      if (first) {
        first = false;
        paramsStr.append("--data \"");
      } else {
        paramsStr.append("&");
      }
      if (equal == -1) {
        paramsStr.append(param).append("=");
      } else {
        paramsStr.append(param, 0, equal).append("=").append(param.substring(equal + 1));
      }
    }
    return !first;
  }

  private static String getValueFromLogLine(final String line) {
    final int start = line.lastIndexOf("\t");
    if (start == -1) {
      return line.trim();
    } else {
      return line.substring(start).trim();
    }
  }

  private static String createCurlBodyString(final List<String> bodyLines) {
    return String.join("\n", bodyLines);
  }

  private static List<String> getValuesForBlock(final String[] lines, final String blockToken) {
    final List<String> values = new ArrayList<>();
    boolean blockStarted = false;

    for (final String line : lines) {
      if (!blockStarted) {
        blockStarted = handleStartBlock(line, blockToken, values);
      } else {
        boolean isNextBlockStarted = handleBlockContent(line, values);
        if (isNextBlockStarted) {
          return values;
        }
      }
    }

    return values;
  }

  private static boolean handleStartBlock(String line, String blockToken, List<String> values) {
    if (line.startsWith(blockToken + ":")) {
      final String v = getValueFromLogLine(line);
      if (!"<none>".equals(v) && !line.trim().equals(v)) {
        values.add(v);
      }
      return true;
    }
    return false;
  }

  private static boolean handleBlockContent(String line, List<String> values) {
    final int tab = line.indexOf("\t");
    final int colon = line.indexOf(":");
    if (colon != -1 && colon < tab) {
      // Next block starts
      return true;
    } else {
      // Add value
      values.add(getValueFromLogLine(line));
      return false;
    }
  }
}
