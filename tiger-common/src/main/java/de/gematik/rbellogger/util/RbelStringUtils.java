package de.gematik.rbellogger.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RbelStringUtils {
  public static String bytesToStringWithoutNonPrintableCharacters(byte[] content, int maxLength) {
    return StringUtils.abbreviate(bytesToStringWithoutNonPrintableCharacters(content), maxLength);
  }

  public static String bytesToStringWithoutNonPrintableCharacters(byte[] content) {
    return new String(content)
        .replace("\r\n", "<CRLF>")
        .replace("\n", "<LF>")
        .replace("\r", "<CR>");
  }
}
