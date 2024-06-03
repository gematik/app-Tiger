package de.gematik.rbellogger.util;

import de.gematik.rbellogger.data.RbelElement;
import java.nio.charset.StandardCharsets;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Pop3Utils {
  public static final String CRLF = "\r\n";

  public static String removeStuffedDot(String line) {
    return line.startsWith(".") ? line.substring(1) : line;
  }

  public static RbelElement createChildElement(RbelElement parent, String value) {
    return new RbelElement(value.getBytes(StandardCharsets.UTF_8), parent);
  }

  public static boolean endsWithCrLf(byte[] c) {
    return c[c.length - 2] == '\r' && c[c.length - 1] == '\n';
  }
}
