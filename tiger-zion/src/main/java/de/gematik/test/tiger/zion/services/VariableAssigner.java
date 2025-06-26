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
package de.gematik.test.tiger.zion.services;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The VariableAssigner class provides a static method for assigning values to variables in a given
 * map.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class VariableAssigner {
  /**
   * Assigns values to variables based on the provided map of assignments.
   *
   * @param assignments the map of variable assignments
   * @param currentElement the current RbelElement
   * @param jexlContext the TigerJexlContext
   */
  public static void doAssignments(
      Map<String, String> assignments, RbelElement currentElement, TigerJexlContext jexlContext) {
    if (assignments == null || assignments.isEmpty()) {
      return;
    }
    final TigerJexlContext localResponseContext =
        jexlContext.withCurrentElement(currentElement).withRootElement(currentElement);

    for (Map.Entry<String, String> entry : assignments.entrySet()) {
      final String newValue =
          Optional.of(entry.getValue())
              .filter(s -> s.startsWith("$."))
              .flatMap(currentElement::findElement)
              .map(el -> el.seekValue(String.class).orElseGet(el::getRawStringContent))
              .map(TigerGlobalConfiguration::resolvePlaceholders)
              .orElseGet(
                  () ->
                      TigerGlobalConfiguration.resolvePlaceholdersWithContext(
                          entry.getValue(), localResponseContext));

      final String key =
          TigerGlobalConfiguration.resolvePlaceholdersWithContext(
              entry.getKey(), localResponseContext);
      jexlContext.put(key, newValue);
    }
  }
}
