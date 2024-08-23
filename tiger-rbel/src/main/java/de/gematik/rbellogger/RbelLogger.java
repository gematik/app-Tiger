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

package de.gematik.rbellogger;

import de.gematik.rbellogger.captures.RbelCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.*;
import de.gematik.rbellogger.converter.listener.RbelJwkReader;
import de.gematik.rbellogger.converter.listener.RbelX5cKeyReader;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.key.RbelKeyManager;
import de.gematik.rbellogger.modifier.RbelModifier;
import java.util.*;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class RbelLogger {

  private static final Logger log = LoggerFactory.getLogger(RbelLogger.class);
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

    rbelConverter.initializeConverters();

    rbelConverter.registerListener(new RbelX5cKeyReader());
    rbelConverter.registerListener(new RbelJwkReader());
    rbelConverter.getPostConversionListeners().addAll(configuration.getPostConversionListener());
    rbelConverter.registerListener(rbelConverter.getRbelValueShader().getPostConversionListener());

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
