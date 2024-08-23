/*
 * Copyright 2024 gematik GmbH
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
 */

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
