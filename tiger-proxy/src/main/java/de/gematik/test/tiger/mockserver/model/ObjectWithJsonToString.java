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

package de.gematik.test.tiger.mockserver.model;

import de.gematik.test.tiger.mockserver.serialization.ObjectMapperFactory;

/*
 * @author jamesdbloom
 */
public abstract class ObjectWithJsonToString extends ObjectWithReflectiveEqualsHashCodeToString {

  private static final String ESCAPED_QUOTE = "\"";

  @Override
  public String toString() {
    try {
      String valueAsString = ObjectMapperFactory.createObjectMapper().writeValueAsString(this);
      if (valueAsString.startsWith(ESCAPED_QUOTE) && valueAsString.endsWith(ESCAPED_QUOTE)) {
        valueAsString = valueAsString.substring(1, valueAsString.length() - 1);
      }
      return valueAsString;
    } catch (Exception e) {
      return super.toString();
    }
  }
}
