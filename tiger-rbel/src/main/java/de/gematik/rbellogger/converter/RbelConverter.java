/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.key.RbelKeyManager;
import de.gematik.test.tiger.common.util.ImmutableDequeFacade;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PUBLIC)
@Getter
@Slf4j
public class RbelConverter {

    @Builder.Default
    private int rbelBufferSizeInMb = 1024;
    @Builder.Default
    private boolean manageBuffer = false;
    private long currentBufferSize = 0;
    private final Deque<RbelElement> messageHistory = new ConcurrentLinkedDeque<>();
    private final Set<String> knownMessageUuids = ConcurrentHashMap.newKeySet();
    private final List<RbelBundleCriterion> bundleCriterionList = new ArrayList<>();
    private final RbelKeyManager rbelKeyManager;
    private final RbelValueShader rbelValueShader = new RbelValueShader();
    private final List<RbelConverterPlugin> postConversionListeners = new ArrayList<>();
    private final Map<Class<? extends RbelElement>, List<BiFunction<RbelElement, RbelConverter, RbelElement>>> preConversionMappers
        = new HashMap<>();
    private final List<RbelConverterPlugin> converterPlugins = new ArrayList<>(List.of(
        new RbelBase64JsonConverter(),
        new RbelUriConverter(),
        new RbelHttpResponseConverter(),
        new RbelHttpRequestConverter(),
        new RbelJwtConverter(),
        new RbelHttpFormDataConverter(),
        new RbelJweConverter(),
        new RbelBearerTokenConverter(),
        new RbelXmlConverter(),
        new RbelJsonConverter(),
        new RbelVauKeyDeriver(),
        new RbelMtomConverter(),
        new RbelX509Converter(),
        new RbelSicctEnvelopeConverter(),
        new RbelSicctCommandConverter(),
        new RbelCetpConverter()
    ));
    @Builder.Default
    private long messageSequenceNumber = 0;
    @Builder.Default
    private int skipParsingWhenMessageLargerThanKb = -1;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public RbelElement convertElement(final byte[] input, RbelElement parentNode) {
        return convertElement(RbelElement.builder()
            .parentNode(parentNode)
            .rawContent(input)
            .build());
    }

    public RbelElement convertElement(final String input, RbelElement parentNode) {
        return convertElement(RbelElement.builder()
            .parentNode(parentNode)
            .rawContent(input.getBytes(Optional.ofNullable(parentNode)
                .map(RbelElement::getElementCharset)
                .orElse(StandardCharsets.UTF_8)))
            .build());
    }

    public RbelElement convertElement(final RbelElement rawInput) {
        log.trace("Converting {}...", rawInput);
        final RbelElement convertedInput = filterInputThroughPreConversionMappers(rawInput);
        boolean elementIsOversized = skipParsingWhenMessageLargerThanKb > -1
            && (convertedInput.getRawContent().length > skipParsingWhenMessageLargerThanKb * 1024);
        for (RbelConverterPlugin plugin : converterPlugins) {
            if (!plugin.ignoreOversize() && elementIsOversized) {
                continue;
            }
            try {
                plugin.consumeElement(convertedInput, this);
            } catch (RuntimeException e) {
                final String msg = "Exception during conversion with plugin '" + plugin.getClass().getName()
                    + "' (" + e.getMessage() + ")";
                log.info(msg, e);
                if (log.isDebugEnabled()) {
                    log.debug("Content in failed conversion-attempt was (B64-encoded) {}",
                        Base64.getEncoder().encodeToString(rawInput.getRawContent()));
                    if (rawInput.getParentNode() != null) {
                        log.debug("Parent-Content in failed conversion-attempt was (B64-encoded) {}",
                            Base64.getEncoder().encodeToString(rawInput.getParentNode().getRawContent()));
                    }
                }
                rawInput.addFacet(new RbelNoteFacet(msg, RbelNoteFacet.NoteStyling.ERROR));
            }
        }
        return convertedInput;
    }

    private Optional<RbelElement> findLastRequest() {
        for (var iterator = messageHistory.descendingIterator(); iterator.hasNext(); ) {
            var element = iterator.next();
            if (element.hasFacet(RbelHttpRequestFacet.class)) {
                return Optional.ofNullable(element);
            }
        }
        return Optional.empty();
    }

    public RbelElement filterInputThroughPreConversionMappers(final RbelElement input) {
        RbelElement value = input;
        for (BiFunction<RbelElement, RbelConverter, RbelElement> mapper : preConversionMappers.entrySet().stream()
            .filter(entry -> input.getClass().isAssignableFrom(entry.getKey()))
            .map(Entry::getValue)
            .flatMap(List::stream)
            .collect(Collectors.toList())) {
            RbelElement newValue = mapper.apply(value, this);
            if (newValue != value) {
                value = filterInputThroughPreConversionMappers(newValue);
            }
        }
        return value;
    }

    public void registerListener(final RbelConverterPlugin listener) {
        postConversionListeners.add(listener);
    }

    public void triggerPostConversionListenerFor(RbelElement element) {
        for (RbelConverterPlugin postConversionListener : postConversionListeners) {
            postConversionListener.consumeElement(element, this);
        }
    }

    public void registerMapper(Class<? extends RbelElement> clazz,
        BiFunction<RbelElement, RbelConverter, RbelElement> mapper) {
        preConversionMappers
            .computeIfAbsent(clazz, key -> new ArrayList<>())
            .add(mapper);
    }

    public void addConverter(RbelConverterPlugin converter) {
        converterPlugins.add(converter);
    }

    public RbelElement parseMessage(@NonNull byte[] content, RbelHostname sender, RbelHostname receiver,
        Optional<ZonedDateTime> transmissionTime) {
        final RbelElement rbelMessage = convertElement(content, null);
        return doMessagePostConversion(rbelMessage, sender, receiver, transmissionTime);
    }

    public RbelElement parseMessage(@NonNull final RbelElement rbelElement, RbelHostname sender, RbelHostname receiver,
        Optional<ZonedDateTime> transmissionTime) {
        final RbelElement rbelMessage = convertElement(rbelElement);
        return doMessagePostConversion(rbelMessage, sender, receiver, transmissionTime);
    }

    public RbelElement doMessagePostConversion(@NonNull final RbelElement rbelElement, RbelHostname sender,
        RbelHostname receiver, Optional<ZonedDateTime> transmissionTime) {
        if (rbelElement.getFacet(RbelHttpResponseFacet.class)
            .map(resp -> resp.getRequest() == null)
            .orElse(false)) {
            final Optional<RbelElement> request = findLastRequest();
            rbelElement.addOrReplaceFacet(
                rbelElement.getFacet(RbelHttpResponseFacet.class)
                    .map(RbelHttpResponseFacet::toBuilder)
                    .orElse(RbelHttpResponseFacet.builder())
                    .request(request.orElse(null))
                    .build());
            request
                .flatMap(req -> req.getFacet(RbelHttpRequestFacet.class))
                .ifPresent(reqFacet -> request.get().addOrReplaceFacet(reqFacet.toBuilder()
                    .response(rbelElement)
                    .build()));
        }

        rbelElement.addFacet(RbelTcpIpMessageFacet.builder()
            .receiver(RbelHostnameFacet.buildRbelHostnameFacet(rbelElement, receiver))
            .sender(RbelHostnameFacet.buildRbelHostnameFacet(rbelElement, sender))
            .sequenceNumber(messageSequenceNumber++)
            .build());

        transmissionTime.ifPresent(
            tt -> rbelElement.addFacet(RbelMessageTimingFacet.builder().transmissionTime(tt).build()));

        rbelElement.triggerPostConversionListener(this);
        synchronized (messageHistory) {
            currentBufferSize += rbelElement.getSize();
            knownMessageUuids.add(rbelElement.getUuid());
            messageHistory.add(rbelElement);
        }
        manageRbelBufferSize();
        return rbelElement;
    }

    public RbelConverter addPostConversionListener(RbelConverterPlugin postConversionListener) {
        postConversionListeners.add(postConversionListener);
        return this;
    }

    public void removeAllConverterPlugins() {
        converterPlugins.clear();
    }

    public void manageRbelBufferSize() {
        if (manageBuffer) {
            synchronized (messageHistory) {
                if (getRbelBufferSizeInMb() <= 0 && !getMessageHistory().isEmpty()) {
                    currentBufferSize = 0;
                    messageHistory.clear();
                    knownMessageUuids.clear();
                }
                if (getRbelBufferSizeInMb() > 0) {
                    long exceedingLimit = getExceedingLimit(currentBufferSize);
                    if (exceedingLimit > 0) {
                        log.trace("Buffer is currently at {} Mb which exceeds the limit of {} Mb",
                            currentBufferSize / (1024 ^ 2), getRbelBufferSizeInMb());
                    }
                    while (exceedingLimit > 0 && !getMessageHistory().isEmpty()) {
                        log.trace("Exceeded buffer size, dropping oldest message in history");
                        final RbelElement messageToDrop = getMessageHistory().getFirst();
                        exceedingLimit -= messageToDrop.getSize();
                        currentBufferSize -= messageToDrop.getSize();
                        knownMessageUuids.remove(messageToDrop.getUuid());
                        getMessageHistory().remove(0);
                    }
                }
            }
        }
    }

    private long getExceedingLimit(long messageHistorySize) {
        return messageHistorySize - ((long) getRbelBufferSizeInMb() * 1024 * 1024);
    }

    public boolean isMessageUuidAlreadyKnown(String msgUuid) {
        return knownMessageUuids.contains(msgUuid);
    }

    public Stream<RbelElement> messagesStreamLatestFirst() {
        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(messageHistory.descendingIterator(), Spliterator.ORDERED),
            false);
    }

    public List<RbelElement> getMessageList() {
        return new ArrayList<>(getMessageHistory());
    }

    public ImmutableDequeFacade<RbelElement> getMessageHistory() {
        return new ImmutableDequeFacade<>(messageHistory);
    }

    public void clearAllMessages() {
        synchronized (messageHistory) {
            currentBufferSize = 0;
            messageHistory.clear();
            knownMessageUuids.clear();
        }
    }

    public void removeMessage(RbelElement rbelMessage) {
        synchronized (messageHistory) {
            final Iterator<RbelElement> iterator = messageHistory.descendingIterator();
            while (iterator.hasNext()) {
                if (iterator.next().equals(rbelMessage)) {
                    iterator.remove();
                    currentBufferSize -= rbelMessage.getSize();
                    knownMessageUuids.remove(rbelMessage.getUuid());
                }
            }
        }
    }
}
