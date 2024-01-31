/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
