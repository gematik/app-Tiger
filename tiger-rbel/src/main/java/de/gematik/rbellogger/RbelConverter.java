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

import static de.gematik.rbellogger.RbelConversionPhase.*;
import static de.gematik.rbellogger.data.core.RbelHostnameFacet.buildRbelHostnameFacet;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.brainpool.BrainpoolCurves;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.key.RbelKeyManager;
import de.gematik.rbellogger.util.ConverterPluginMap;
import de.gematik.rbellogger.util.RbelValueShader;
import de.gematik.test.tiger.common.config.TigerTypedConfigurationKey;
import de.gematik.test.tiger.common.util.TigerSecurityProviderInitialiser;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import lombok.*;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PUBLIC, toBuilder = true)
@Slf4j
public class RbelConverter implements RbelConverterInterface {

  private static final String RECEIVER_METADATA_KEY = "receiver";
  private static final String SENDER_METADATA_KEY = "sender";

  static {
    BrainpoolCurves.init();
    TigerSecurityProviderInitialiser.initialize();
  }

  @Getter private final RbelKeyManager rbelKeyManager;
  @Getter private final RbelValueShader rbelValueShader = new RbelValueShader();
  @Getter private final ConverterPluginMap converterPlugins = new ConverterPluginMap();
  private final ExecutorService executorService =
      new ThreadPoolExecutor(
          0,
          Integer.MAX_VALUE,
          60L,
          TimeUnit.SECONDS,
          new SynchronousQueue<>(),
          new ThreadFactoryBuilder().setNameFormat("rbel-converter-thread-%d").build());

  @Builder.Default int rbelBufferSizeInMb = 1024;
  @Builder.Default boolean manageBuffer = false;
  @Builder.Default int skipParsingWhenMessageLargerThanKb = -1;
  @Builder.Default List<String> activateRbelParsingFor = List.of();
  @Builder.Default private volatile boolean shallInitializeConverters = true;
  @Builder.Default @Getter boolean isActivateRbelParsing = true;
  @Builder.Default @Setter @Getter String name = "<>";

  @Builder.Default
  private List<RbelConversionPhase> conversionPhases =
      List.of(PREPARATION, PROTOCOL_PARSING, CONTENT_PARSING, CONTENT_ENRICHMENT, TRANSMISSION);

  @Getter(lazy = true)
  @Delegate
  private final RbelMessageHistory history = new RbelMessageHistory(this);

  public static final TigerTypedConfigurationKey<Integer> RAW_STRING_MAX_TRACE_LENGTH =
      new TigerTypedConfigurationKey<>(
          "tiger.rbel.rawstring.max.trace.length", Integer.class, 1000);

  public void initializeConverters(RbelConfiguration rbelConfiguration) {
    if (shallInitializeConverters) {
      // the outside check is done to avoid the synchronized overhead for most calls
      synchronized (converterPlugins) {
        if (shallInitializeConverters) {
          new RbelConverterInitializer(this, rbelConfiguration, activateRbelParsingFor)
              .addConverters();
          shallInitializeConverters = false;
        }
      }
    }
  }

  public RbelElement convertElement(RbelElement rbelElement) {
    return convertElement(rbelElement, conversionPhases);
  }

  public RbelElement convertElement(
      RbelElement rbelElement, List<RbelConversionPhase> conversionPhases) {
    return new RbelConversionExecutor(
            this, rbelElement, skipParsingWhenMessageLargerThanKb, rbelKeyManager, conversionPhases)
        .execute();
  }

  public RbelElement convertElement(final byte[] input, RbelElement parentNode) {
    return convertElement(RbelElement.builder().parentNode(parentNode).rawContent(input).build());
  }

  public List<RbelConverterPlugin> getPlugins(Predicate<RbelConverterPlugin> filter) {
    synchronized (converterPlugins) {
      return converterPlugins.stream().filter(filter).toList();
    }
  }

  public RbelElement convertElement(final String input, RbelElement parentNode) {
    return convertElement(
        RbelElement.builder()
            .parentNode(parentNode)
            .rawContent(
                input.getBytes(
                    Optional.ofNullable(parentNode)
                        .map(RbelElement::getElementCharset)
                        .orElse(StandardCharsets.UTF_8)))
            .build());
  }

  public void addConverter(RbelConverterPlugin converter) {
    converterPlugins.put(converter);
  }

  public RbelElement parseMessage(byte[] content, RbelMessageMetadata conversionMetadata) {
    final RbelElement messageElement = RbelElement.builder().rawContent(content).build();
    return parseMessage(messageElement, conversionMetadata);
  }

  public RbelElement parseMessage(
      @NonNull final RbelElement message, @NonNull final RbelMessageMetadata conversionMetadata) {
    try {
      return parseMessageAsync(message, conversionMetadata)
          .exceptionally(
              t -> {
                log.error("Error while parsing message", t);
                return null;
              })
          .get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RbelConversionException(e);
    } catch (ExecutionException e) {
      throw new RbelConversionException(e);
    }
  }

  public CompletableFuture<RbelElement> parseMessageAsync(
      @NonNull final RbelElement messageElement,
      @NonNull final RbelMessageMetadata conversionMetadata) {
    if (messageElement.getContent().isNull()) {
      throw new RbelConversionException("content is empty");
    }
    if (getKnownMessageUuids().isAlreadyConverted(messageElement.getUuid())) {
      log.atTrace()
          .addArgument(this::getName)
          .addArgument(messageElement::getUuid)
          .log("{} skipping parsing of message with UUID {}: UUID already known");
      return CompletableFuture.failedFuture(new RbelConversionException("UUID is already known"));
    }
    addMessageToHistory(messageElement, conversionMetadata);

    messageElement.addFacet(conversionMetadata);

    return CompletableFuture.supplyAsync(() -> convertElement(messageElement), executorService);
  }

  public void addMessageToHistory(RbelElement rbelElement, RbelMessageMetadata conversionMetadata) {
    getHistory().addMessageToHistory(rbelElement);
    addTcpIpFacet(rbelElement, conversionMetadata);
  }

  public void addMessageToHistory(RbelElement rbelElement) {
    addMessageToHistory(rbelElement, null);
  }

  private void addTcpIpFacet(RbelElement rbelElement, RbelMessageMetadata conversionMetadata) {
    // Use the builder pattern as in RbelConverter
    RbelTcpIpMessageFacet.RbelTcpIpMessageFacetBuilder facetBuilder =
        rbelElement
            .getFacet(RbelTcpIpMessageFacet.class)
            .map(RbelTcpIpMessageFacet::toBuilder)
            .orElseGet(
                () ->
                    RbelTcpIpMessageFacet.builder()
                        .sender(buildRbelHostnameFacet(rbelElement, null))
                        .receiver(buildRbelHostnameFacet(rbelElement, null)));
    if (conversionMetadata != null) {
      RbelMessageMetadata.MESSAGE_SENDER
          .getValue(conversionMetadata)
          .map(sender -> buildRbelHostnameFacet(rbelElement, sender))
          .ifPresent(facetBuilder::sender);
      RbelMessageMetadata.MESSAGE_RECEIVER
          .getValue(conversionMetadata)
          .map(receiver -> buildRbelHostnameFacet(rbelElement, receiver))
          .ifPresent(facetBuilder::receiver);
    }
    rbelElement.addOrReplaceFacet(facetBuilder.build());
  }

  public void deactivateRbelParsing() {
    isActivateRbelParsing = false;
    conversionPhases = List.of(PREPARATION, CONTENT_ENRICHMENT, TRANSMISSION);
  }

  /**
   * Triggers the "transmission" phase on the given element. The element is not stored in the
   * messages list
   *
   * @param rbelElement
   */
  public void transmitElement(RbelElement rbelElement) {
    convertElement(
        rbelElement, List.of(RbelConversionPhase.PREPARATION, RbelConversionPhase.TRANSMISSION));
  }

  private static @NonNull List<String> getTrimmedListElements(String commaSeparatedList) {
    return Arrays.stream(commaSeparatedList.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  public void deactivateParsingFor(String parsersToDeactivate) {
    List<String> idsToDeactivate = getTrimmedListElements(parsersToDeactivate);
    getConverterPlugins().stream()
        .filter(plugin -> plugin.isParserFor(idsToDeactivate))
        .forEach(RbelConverterPlugin::deactivate);
  }

  public void activateParsingForAll() {
    getConverterPlugins().forEach(RbelConverterPlugin::activate);
  }

  @Deprecated(since = "4.0.0")
  public void reactivateParsingForAll() {
    activateParsingForAll();
  }

  public void deactivateOptionalParsing() {
    getConverterPlugins().stream()
        .filter(RbelConverterPlugin::isOptional)
        .forEach(RbelConverterPlugin::deactivate);
  }

  public void activateParsingFor(String parsersToActivate) {
    List<String> idsToActivate = getTrimmedListElements(parsersToActivate);
    getConverterPlugins().stream()
        .filter(plugin -> plugin.isParserFor(idsToActivate))
        .forEach(RbelConverterPlugin::activate);
  }

  public void reinitializeConverters(RbelConfiguration config) {
    synchronized (converterPlugins) {
      shallInitializeConverters = true;
      activateRbelParsingFor = config.getActivateRbelParsingFor();
      converterPlugins.clear();
      initializeConverters(config);
    }
  }

  /**
   * Gives a view of the current messages. This view includes messages that are not yet fully
   * parsed.
   */
  public RbelMessageHistory.Facade getMessageHistoryAsync() {
    return getHistory().getMessageHistoryAsync();
  }

  public void waitForMessageAndPartnersToBeFullyConverted(RbelElement message) {
    waitForGivenElementToBeParsed(message);
    message.getFacets().forEach(facet -> facet.waitForFacetToHaveParsedPartners(message, this));
  }
}
