package de.gematik.rbellogger.writer;

import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum RbelContentType {
  XML("application/xml", true),
  JSON("application/json", true),
  JWT("application/jwt", false),
  JWE("application/octet-stream", false),
  URL("text/plain", false),
  BEARER_TOKEN("text/plain", false);

  @Getter final String contentTypeString;
  @Getter final boolean isTransitive;

  public static RbelContentType seekValueFor(String rawValue) {
    String compareValue = rawValue.trim().toUpperCase();
    return Stream.of(values())
        .filter(candidate -> candidate.name().equals(compareValue))
        .findFirst()
        .orElseThrow(
            () -> new RuntimeException("Could not match content type '" + rawValue + "'!"));
  }
}
