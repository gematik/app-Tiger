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
package de.gematik.rbellogger.facets.otlp;

import java.util.Locale;
import java.util.Optional;
import lombok.Getter;

/** Known OTLP signal types and their canonical HTTP path markers. */
public enum RbelOtlpSignal {
  TRACES("traces", "/v1/traces"),
  METRICS("metrics", "/v1/metrics"),
  LOGS("logs", "/v1/logs");

  @Getter
  private final String displayName;
  private final String pathMarker;

  RbelOtlpSignal(String displayName, String pathMarker) {
    this.displayName = displayName;
    this.pathMarker = pathMarker;
  }

  /**
   * Resolves an OTLP signal type from an HTTP request path.
   *
   * @param path HTTP request path to inspect.
   * @return optional signal when a known OTLP endpoint is present in the path.
   */
  public static Optional<RbelOtlpSignal> fromPath(String path) {
    if (path == null || path.isBlank()) {
      return Optional.empty();
    }
    var normalized = path.toLowerCase(Locale.ROOT);
    for (var signal : values()) {
      if (normalized.contains(signal.pathMarker)) {
        return Optional.of(signal);
      }
    }
    return Optional.empty();
  }
}
