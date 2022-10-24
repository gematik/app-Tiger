/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.configuration;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelCapturer;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.converter.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.key.RbelKey;
import java.security.Key;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RbelConfiguration {

    @Builder.Default
    private List<RbelConverterPlugin> postConversionListener = new ArrayList<>();
    @Builder.Default
    private Map<Class<? extends RbelElement>, List<BiFunction<RbelElement, RbelConverter, RbelElement>>> preConversionMappers
        = new HashMap<>();
    @Builder.Default
    private List<Consumer<RbelConverter>> initializers = new ArrayList<>();
    @Builder.Default
    private Map<String, RbelKey> keys = new HashMap<>();
    private RbelCapturer capturer;
    @Builder.Default
    private boolean activateAsn1Parsing = true;
    private RbelFileSaveInfo fileSaveInfo;
    @Builder.Default
    private int rbelBufferSizeInMb = 1024;
    @Builder.Default
    private int skipParsingWhenMessageLargerThanMb = 16;
    @Builder.Default
    private boolean manageBuffer = false;

    public RbelConfiguration addPostConversionListener(RbelConverterPlugin listener) {
        postConversionListener.add(listener);
        return this;
    }

    public RbelConfiguration withFileSaveInfo(RbelFileSaveInfo fileSaveInfo) {
        this.fileSaveInfo = fileSaveInfo;
        return this;
    }

    public <T extends RbelElement> RbelConfiguration addPreConversionMapper(Class<T> clazz,
        BiFunction<T, RbelConverter, RbelElement> mapper) {
        if (!preConversionMappers.containsKey(clazz)) {
            preConversionMappers.put(clazz, new ArrayList<>());
        }
        preConversionMappers.get(clazz).add((rawKey, context) -> mapper.apply((T) rawKey, context));
        return this;
    }

    public RbelConfiguration addInitializer(Consumer<RbelConverter> initializer) {
        initializers.add(initializer);
        return this;
    }

    public RbelConfiguration addKey(final String keyId, final Key key, final int precedence) {
        keys.put(keyId, RbelKey.builder()
            .key(key)
            .keyName(keyId)
            .precedence(precedence)
            .build());
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

    public RbelLogger constructRbelLogger() {
        return RbelLogger.build(this);
    }
}
