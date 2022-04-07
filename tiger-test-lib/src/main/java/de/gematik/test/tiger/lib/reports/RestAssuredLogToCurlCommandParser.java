/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.reports;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class RestAssuredLogToCurlCommandParser {
    public static List<String> convertRestAssuredLogToCurlCalls(final String raLog) {
        List<String> requestLogs = new ArrayList<>();

        final String[] lines = raLog.split("\\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line.trim().startsWith("Request method:")) {
                if (sb.length() > 0) {
                    requestLogs.add(sb.toString());
                    sb.setLength(0);
                }
            }
            sb.append(line).append("\n");
        }
        if (sb.length() > 0) {
            requestLogs.add(sb.toString());
        }
        return requestLogs;
    }

    public static String parseCurlCommandFromRestAssuredLog(final String rALogDetails) {
        final String[] lines = rALogDetails.split("\\n");
        if (lines.length == 0) {
            return "";
        }
        final String uri = Stream.of(lines)
            .filter(l -> l.trim().startsWith("Request URI:"))
            .map(RestAssuredLogToCurlCommandParser::getValueFromLogLine)
            .findFirst().orElse(null);
        final String method = Stream.of(lines)
            .filter(l -> l.trim().startsWith("Request method:"))
            .map(RestAssuredLogToCurlCommandParser::getValueFromLogLine)
            .findFirst().orElse(null);

        final StringBuilder curlCmd = new StringBuilder("curl -v ");
        if (uri != null && method != null) {
            // add headers
            final List<String> headers = getValuesForBlock(lines, "Headers");
            for (final String header : headers) {
                final int equal = header.indexOf("=");
                curlCmd.append("-H \"").append(header, 0, equal)
                    .append(": ").append(header.substring(equal + 1)).append("\" ");
            }

            switch (method) {
                case "GET":
                    curlCmd.append("-X GET \"").append(uri).append("\" ");
                    break;
                case "POST":
                    // add form params
                    final StringBuilder paramsStr = new StringBuilder();
                    if (createCurlParamString(paramsStr, getValuesForBlock(lines, "Form params"))) {
                        curlCmd.append(" ").append(paramsStr).append("\" ");
                    }
                    curlCmd.append("-X POST \"").append(uri).append("\" ");
                    break;
                case "DELETE":
                    curlCmd.append("-X DELETE \"").append(uri).append("\" ");
                    break;
                case "PUT":
                    curlCmd.append("-X PUT -d '").append(createCurlBodyString(getValuesForBlock(lines, "Body")))
                        .append("' \"").append(uri).append("\" ");
                    break;
            }
        } else {
            curlCmd.append("Unable to parse log data");
        }
        return curlCmd.toString();
    }

    private static boolean createCurlParamString(final StringBuilder paramsStr, final List<String> params) {
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
                paramsStr.append(param, 0, equal)
                    .append("=").append(param.substring(equal + 1));
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
            if (!blockStarted && line.startsWith(blockToken + ":")) {
                blockStarted = true;
                final String v = getValueFromLogLine(line);
                if ("<none>".equals(v)) {
                    return new ArrayList<>();
                }
                if (!line.trim().equals(v)) {
                    values.add(v);
                }
            } else if (blockStarted) {
                final int tab = line.indexOf("\t");
                final int colon = line.indexOf(":");
                if (colon != -1 && colon < tab) {
                    // next block starts
                    return values;
                } else {
                    // add value
                    values.add(getValueFromLogLine(line));
                }
            }
        }
        return values;
    }
}
