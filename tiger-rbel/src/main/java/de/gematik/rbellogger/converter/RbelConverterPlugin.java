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

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import lombok.Getter;
import lombok.val;
import org.apache.commons.lang3.ArrayUtils;

public abstract class RbelConverterPlugin {

  @Getter(lazy = true)
  private final Set<String> parserIdentifiers = initializeParserIdentifiers();

  private boolean isActive = true;

  public abstract void consumeElement(RbelElement rbelElement, RbelConverter converter);

  public boolean ignoreOversize() {
    return false;
  }

  public boolean isParserFor(String parserIdentifier) {
    return getParserIdentifiers().contains(parserIdentifier);
  }

  public boolean isParserForAny(List<String> pluginIds) {
    for (String pluginId : pluginIds) {
      if (isParserFor(pluginId)) {
        return true;
      }
    }
    return false;
  }

  public boolean isActive() {
    return isActive;
  }

  public void activate() {
    isActive = true;
  }

  public void deactivate() {
    isActive = false;
  }

  private Set<String> initializeParserIdentifiers() {
    val parserIds = new HashSet<String>();
    if (this.getClass().isAnnotationPresent(ConverterInfo.class)) {
      val converterInfo = this.getClass().getAnnotation(ConverterInfo.class);
      parserIds.addAll(List.of(ArrayUtils.nullToEmpty(converterInfo.onlyActivateFor())));
    }
    return parserIds;
  }

  public static RbelConverterPlugin createPlugin(BiConsumer<RbelElement, RbelConverter> consumer) {
    return new RbelConverterPlugin() {

      @Override
      public void consumeElement(RbelElement rbelElement, RbelConverter converter) {
        consumer.accept(rbelElement, converter);
      }
    };
  }
}
