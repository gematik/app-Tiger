package de.gematik.rbellogger.writer;

import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum RbelContentType {
  XML("application/xml"),
  JSON("application/json"),
  JWT("application/jwt"),
  JWE("application/octet-stream"),
  URL("text/plain"),
  BEARER_TOKEN("text/plain");

  @Getter final String contentTypeString;

  public static RbelContentType seekValueFor(String rawValue) {
    String compareValue = rawValue.trim().toUpperCase();
    return Stream.of(values())
        .filter(candidate -> candidate.name().equals(compareValue))
        .findFirst()
        .orElseThrow(
            () -> new RuntimeException("Could not match content type '" + rawValue + "'!"));
  }
}
