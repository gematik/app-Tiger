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
package de.gematik.rbellogger.util;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import lombok.val;
import org.junit.jupiter.api.Test;

class ConverterPluginMapTest {

  @Test
  void testAddAndCheckItems() {
    ConverterPluginMap map = new ConverterPluginMap();
    val plugin1 = generatePlugin(0, RbelConversionPhase.PREPARATION);
    val plugin2 = generatePlugin(10, RbelConversionPhase.CONTENT_PARSING);
    val plugin3 = generatePlugin(0, RbelConversionPhase.CONTENT_PARSING);
    val plugin4 = generatePlugin(100, RbelConversionPhase.CONTENT_PARSING);
    val plugin5 = generatePlugin(0, RbelConversionPhase.CONTENT_PARSING);

    map.put(plugin1);
    map.put(plugin2);
    map.put(plugin3);
    map.put(plugin4);
    map.put(plugin5);

    val preProcessingPlugins = map.get(RbelConversionPhase.PREPARATION);
    val postProcessingPlugins = map.get(RbelConversionPhase.CONTENT_PARSING);

    assertThat(preProcessingPlugins).hasSize(1).contains(plugin1);
    assertThat(postProcessingPlugins)
        .hasSize(4)
        .containsExactly(plugin4, plugin2, plugin3, plugin5);
  }

  private static RbelConverterPlugin generatePlugin(int priority, RbelConversionPhase phase) {
    return new RbelConverterPlugin() {
      @Override
      public RbelConversionPhase getPhase() {
        return phase;
      }

      @Override
      public int getPriority() {
        return priority;
      }

      @Override
      public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {}

      @Override
      public String toString() {
        return "Plugin with {"
            + "priority="
            + priority
            + ", phase="
            + phase
            + " at position "
            + System.identityHashCode(this)
            + "}";
      }
    };
  }
}
