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

package de.gematik.rbellogger.configuration;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelCapturer;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.converter.RbelConverterPlugin;
import de.gematik.rbellogger.key.RbelKey;
import java.security.Key;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
public class RbelConfiguration {

  @Builder.Default private List<RbelConverterPlugin> postConversionListener = new ArrayList<>();
  @Builder.Default private List<String> activateRbelParsingFor = new ArrayList<>();
  @Builder.Default private List<Consumer<RbelConverter>> initializers = new ArrayList<>();
  @Builder.Default private Map<String, RbelKey> keys = new HashMap<>();
  private RbelCapturer capturer;
  @Builder.Default private int rbelBufferSizeInMb = 1024;
  @Builder.Default private int skipParsingWhenMessageLargerThanKb = 16_000;
  @Builder.Default private boolean manageBuffer = false;
  private Boolean lenientHttpParsing;

  public RbelConfiguration addPostConversionListener(RbelConverterPlugin listener) {
    postConversionListener.add(listener);
    return this;
  }

  public RbelConfiguration addInitializer(Consumer<RbelConverter> initializer) {
    initializers.add(initializer);
    return this;
  }

  public RbelConfiguration addKey(final String keyId, final Key key, final int precedence) {
    keys.put(keyId, RbelKey.builder().key(key).keyName(keyId).precedence(precedence).build());
    return this;
  }

  public RbelConfiguration addCapturer(RbelCapturer capturer) {
    this.capturer = capturer;
    return this;
  }

  public RbelConfiguration activateConversionFor(String key) {
    this.activateRbelParsingFor.add(key);
    return this;
  }

  public RbelLogger constructRbelLogger() {
    return RbelLogger.build(this);
  }
}
