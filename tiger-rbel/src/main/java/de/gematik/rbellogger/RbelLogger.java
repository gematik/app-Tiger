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

import de.gematik.rbellogger.captures.RbelCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.jose.RbelJwkReader;
import de.gematik.rbellogger.facets.jose.RbelX5cKeyReader;
import de.gematik.rbellogger.key.RbelKeyManager;
import de.gematik.rbellogger.modifier.RbelModifier;
import de.gematik.rbellogger.util.RbelValueShader;
import java.util.*;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class RbelLogger {

  private final RbelConverter rbelConverter;
  private final RbelCapturer rbelCapturer;
  private final RbelValueShader valueShader;
  private final RbelKeyManager rbelKeyManager;
  private final RbelModifier rbelModifier;

  public static RbelLogger build() {
    return build(new RbelConfiguration());
  }

  public static RbelLogger build(final RbelConfiguration configuration) {
    Objects.requireNonNull(configuration);

    final RbelConverter rbelConverter =
        RbelConverter.builder()
            .rbelKeyManager(new RbelKeyManager())
            .manageBuffer(configuration.isManageBuffer())
            .rbelBufferSizeInMb(configuration.getRbelBufferSizeInMb())
            .skipParsingWhenMessageLargerThanKb(
                configuration.getSkipParsingWhenMessageLargerThanKb())
            .activateRbelParsingFor(configuration.getActivateRbelParsingFor())
            .build();

    rbelConverter.initializeConverters(configuration);

    rbelConverter.addConverter(new RbelJwkReader());
    rbelConverter.addConverter(new RbelX5cKeyReader());
    configuration.getPostConversionListener().forEach(rbelConverter::addConverter);
    rbelConverter.addConverter(rbelConverter.getRbelValueShader().getPostConversionListener());

    for (Consumer<RbelConverter> initializer : configuration.getInitializers()) {
      initializer.accept(rbelConverter);
    }

    rbelConverter.getRbelKeyManager().addAll(configuration.getKeys());

    if (configuration.getCapturer() != null) {
      configuration.getCapturer().setRbelConverter(rbelConverter);
    }

    return RbelLogger.builder()
        .rbelConverter(rbelConverter)
        .rbelCapturer(configuration.getCapturer())
        .rbelKeyManager(rbelConverter.getRbelKeyManager())
        .rbelModifier(new RbelModifier(rbelConverter.getRbelKeyManager(), rbelConverter))
        .valueShader(rbelConverter.getRbelValueShader())
        .build();
  }

  /**
   * Returns a list of all fully parsed messages. This list does not include messages that are not
   * parsed yet. To guarantee consistent sequence numbers the list stops before the first unparsed
   * message.
   */
  public List<RbelElement> getMessageList() {
    return getRbelConverter().getMessageList();
  }

  /**
   * Gives a view of the current messages. This view includes messages that are not yet fully
   * parsed.
   */
  public Deque<RbelElement> getMessageHistory() {
    return rbelConverter.getMessageHistoryAsync();
  }

  public void clearAllMessages() {
    rbelConverter.clearAllMessages();
  }
}
