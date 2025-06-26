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
package de.gematik.rbellogger;

import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.*;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import lombok.Getter;
import lombok.val;
import org.apache.commons.lang3.ArrayUtils;

public abstract class RbelConverterPlugin {
  @Getter(lazy = true)
  private final Set<String> parserIdentifiers = initializeParserIdentifiers();

  // higher priority: executed sooner
  public int getPriority() {
    return 0;
  }

  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.CONTENT_PARSING;
  }

  public abstract void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter);

  private boolean isActive = true;

  public boolean isOptional() {
    return Optional.ofNullable(getClass().getAnnotation(ConverterInfo.class))
        .map(ConverterInfo::onlyActivateFor)
        .filter(ArrayUtils::isNotEmpty)
        .isPresent();
  }

  public boolean skipParsingOversizedContent() {
    return false;
  }

  public boolean isParserFor(List<String> pluginIds) {
    return pluginIds.stream().anyMatch(getParserIdentifiers()::contains);
  }

  private Set<String> initializeParserIdentifiers() {
    val parserIds = new HashSet<String>();
    if (this.getClass().isAnnotationPresent(ConverterInfo.class)) {
      val converterInfo = this.getClass().getAnnotation(ConverterInfo.class);
      parserIds.addAll(List.of(ArrayUtils.nullToEmpty(converterInfo.onlyActivateFor())));
    }
    return parserIds;
  }

  public void activate() {
    isActive = true;
  }

  public void deactivate() {
    isActive = false;
  }

  public void doConversionIfActive(
      RbelElement convertedInput, RbelConversionExecutor rbelConversionExecutor) {
    if (isActive) {
      consumeElement(convertedInput, rbelConversionExecutor);
    }
  }

  public static RbelConverterPlugin createPlugin(
      BiConsumer<RbelElement, RbelConversionExecutor> consumer) {
    return new RbelConverterPlugin() {

      @Override
      public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
        consumer.accept(rbelElement, converter);
      }
    };
  }

  public static boolean messageIsCompleteOrParsingDeactivated(RbelElement message) {
    return message.getFacets().stream()
        .anyMatch(
            f ->
                f instanceof RbelRootFacet
                    || f instanceof RbelResponseFacet
                    || f instanceof RbelRequestFacet
                    || f instanceof TracingMessagePairFacet
                    || f instanceof UnparsedChunkFacet);
  }
}
