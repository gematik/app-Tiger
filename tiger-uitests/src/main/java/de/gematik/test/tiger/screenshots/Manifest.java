/*
 * Copyright 2026 gematik GmbH
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
package de.gematik.test.tiger.screenshots;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** The manifest.json that tracks the current archive state in the store. */
@JsonIgnoreProperties(ignoreUnknown = true)
record Manifest(String baseline, int sequence, String timestamp) {

  static final String MANIFEST_JSON = "manifest.json";
  static final String MANIFEST_HISTORY_JSON = "manifest-history.json";
  static final String CACHE_MANIFEST_JSON = ".cache-manifest.json";
  static final String BASELINE_ZIP = "baseline.zip";
  static final String DELTA_ZIP = "delta.zip";
  static final String BASELINE_PREFIX = "screenshots-baseline-";

  private static final ObjectMapper MAPPER =
      JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

  static Manifest read(Path file) {
    return MAPPER.readValue(file.toFile(), Manifest.class);
  }

  void write(Path file) {
    MAPPER.writeValue(file.toFile(), this);
  }

  static String nowTimestamp() {
    return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .format(Instant.now().atZone(ZoneOffset.UTC));
  }
}
