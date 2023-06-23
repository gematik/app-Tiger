package de.gematik.rbellogger.writer;

import java.util.stream.Stream;

public enum RbelContentType {
    XML,
    JSON,
    JWT,
    JWE,
    URL,
    BEARER_TOKEN;

    public static RbelContentType seekValueFor(String rawValue) {
        String compareValue = rawValue.trim().toUpperCase();
        return Stream.of(values())
            .filter(candidate -> candidate.name().equals(compareValue))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Could not match content type '" + rawValue + "'!"));
    }
}
