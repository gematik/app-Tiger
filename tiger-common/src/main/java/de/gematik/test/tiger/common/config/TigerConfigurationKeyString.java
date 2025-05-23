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
package de.gematik.test.tiger.common.config;

import java.util.List;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class TigerConfigurationKeyString {
  private static final List<Character> FORBIDDEN_CHARACTERS = List.of('{', '}', '|');

  private final String value;

  public static TigerConfigurationKeyString wrapAsKey(String value) {
    for (Character c : FORBIDDEN_CHARACTERS) {
      value = value.replace(c, '_');
    }
    return new TigerConfigurationKeyString(value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o instanceof String asString) return value.equalsIgnoreCase(asString);

    if (o instanceof TigerConfigurationKeyString asTigerString) {
      return value != null
          ? value.equalsIgnoreCase(asTigerString.value)
          : asTigerString.value == null;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return value != null ? value.toLowerCase().hashCode() : 0;
  }

  public String asLowerCase() {
    return value.toLowerCase();
  }

  public String asString() {
    return value;
  }

  public boolean isNotEmptyKey() {
    return StringUtils.isNotEmpty(value);
  }
}
