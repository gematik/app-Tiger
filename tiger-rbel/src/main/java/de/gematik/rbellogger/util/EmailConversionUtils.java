package de.gematik.rbellogger.util;

import de.gematik.rbellogger.data.RbelElement;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EmailConversionUtils {
  public static final String CRLF = "\r\n";
  public static final String CRLF_DOT_CRLF = CRLF + "." + CRLF;

  public static RbelElement createChildElement(RbelElement parent, String value) {
    return new RbelElement(value.getBytes(StandardCharsets.UTF_8), parent);
  }

  public static boolean endsWithCrLf(byte[] c) {
    return c[c.length - 2] == '\r' && c[c.length - 1] == '\n';
  }

  public static RbelElement parseMailBody(RbelElement element, String[] lines) {
    if (lines.length > 2) {
      var body = extractBodyAndRemoveStuffedDots(lines);
      return createChildElement(element, body);
    }
    return null;
  }

  private static String extractBodyAndRemoveStuffedDots(String[] lines) {
    return Arrays.asList(lines).subList(1, lines.length - 2).stream()
        .map(EmailConversionUtils::removeStuffedDot)
        .collect(Collectors.joining(CRLF));
  }

  public static String removeStuffedDot(String line) {
    return line.startsWith(".") ? line.substring(1) : line;
  }

  public static String duplicateDotsAtLineBegins(String input) {
    return Stream.of(input.split("\r\n", -1))
        .map(line -> line.startsWith(".") ? "." + line : line)
        .collect(Collectors.joining("\r\n"));
  }
}
