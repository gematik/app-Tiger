/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
  @Builder.Default private List<RbelConverterPlugin> additionalConverters = new ArrayList<>();

  @Builder.Default private List<Consumer<RbelConverter>> initializers = new ArrayList<>();
  @Builder.Default private Map<String, RbelKey> keys = new HashMap<>();
  private RbelCapturer capturer;
  @Builder.Default private boolean activateAsn1Parsing = true;
  @Builder.Default private boolean activateVauEpa3Parsing = false;
  @Builder.Default private int rbelBufferSizeInMb = 1024;
  @Builder.Default private int skipParsingWhenMessageLargerThanKb = 16_000;
  @Builder.Default private boolean manageBuffer = false;

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

  public RbelConfiguration setActivateAsn1Parsing(boolean activateAsn1Parsing) {
    this.activateAsn1Parsing = activateAsn1Parsing;
    return this;
  }

  public RbelConfiguration addAdditionalConverter(RbelConverterPlugin converter) {
    additionalConverters.add(converter);
    return this;
  }

  public RbelLogger constructRbelLogger() {
    return RbelLogger.build(this);
  }
}
