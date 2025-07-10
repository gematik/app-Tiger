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
package de.gematik.test.tiger.proxy.handler;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.RbelMessageMetadata.RbelMetadataValue;
import de.gematik.rbellogger.data.core.*;
import de.gematik.test.tiger.proxy.AbstractTigerProxy;
import de.gematik.test.tiger.proxy.data.TcpConnectionEntry;
import de.gematik.test.tiger.proxy.data.TcpIpConnectionIdentifier;
import de.gematik.test.tiger.util.AsyncByteQueue;
import de.gematik.test.tiger.util.DeterministicUuidGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.val;

@RequiredArgsConstructor
public class SingleConnectionParser {
  public static final RbelMetadataValue<Boolean> IS_PROPAGATED_CHUNK_FROM_UPSTREAM_TIGER_PROXY =
      new RbelMetadataValue<>("propagatedMessageChunk", Boolean.class);

  private final BundledServerNamesAdder bundledServerNamesAdder = new BundledServerNamesAdder();

  private final ExecutorService executor;
  private final BinaryExchangeHandler binaryExchangeHandler;
  private final RbelConverter rbelConverter;
  private final AsyncByteQueue bufferedParts;
  private final org.slf4j.Logger log;

  private String lastMessageUuid = null;

  public SingleConnectionParser(
      TcpIpConnectionIdentifier connectionIdentifier,
      AbstractTigerProxy tigerProxy,
      BinaryExchangeHandler binaryExchangeHandler) {
    this.binaryExchangeHandler = binaryExchangeHandler;
    this.rbelConverter = tigerProxy.getRbelLogger().getRbelConverter();
    this.bufferedParts = new AsyncByteQueue(connectionIdentifier);
    this.executor = tigerProxy.getExecutor();
    log =
        org.slf4j.LoggerFactory.getLogger(
            tigerProxy.getName().orElse("TigerProxy") + "-ConnectionParser");
  }

  public SingleConnectionParser(
      TcpIpConnectionIdentifier connectionIdentifier,
      ExecutorService executor,
      RbelConverter rbelConverter,
      BinaryExchangeHandler binaryExchangeHandler) {
    this.binaryExchangeHandler = binaryExchangeHandler;
    this.rbelConverter = rbelConverter;
    this.executor = executor;
    this.bufferedParts = new AsyncByteQueue(connectionIdentifier);
    log = org.slf4j.LoggerFactory.getLogger(this.getClass());
  }

  private List<RbelElement> handleException(
      Throwable throwable, TcpIpConnectionIdentifier direction, TcpConnectionEntry entry) {
    log.warn("Exception while parsing buffered content for message {}", entry.getUuid(), throwable);
    binaryExchangeHandler.propagateExceptionMessageSafe(
        throwable,
        RbelHostname.create(direction.sender()),
        RbelHostname.create(direction.receiver()));
    return List.of();
  }

  public CompletableFuture<List<RbelElement>> bufferNewPart(TcpConnectionEntry entry) {
    val bufferedEntry = bufferedParts.write(entry);
    CompletableFuture.runAsync(() -> propagateNewChunk(bufferedEntry), executor)
        .exceptionally(
            e -> {
              log.warn(
                  "Exception while propagating new chunk for message {}: {}",
                  bufferedEntry.getUuid(),
                  e.getMessage(),
                  e);
              return null;
            });
    return CompletableFuture.supplyAsync(this::parseAllAvailableMessages, executor)
        .exceptionally(e -> handleException(e, bufferedEntry.getConnectionIdentifier(), entry));
  }

  private void propagateNewChunk(TcpConnectionEntry entry) {
    if (entry
            .getAdditionalData()
            .getOrDefault(IS_PROPAGATED_CHUNK_FROM_UPSTREAM_TIGER_PROXY.getKey(), false)
        == Boolean.FALSE) {
      log.atTrace()
          .addArgument(entry::getUuid)
          .log("Skipping propagation of local message chunk {}");
      return;
    }
    var messageElement =
        RbelElement.builder().uuid(entry.getUuid()).content(entry.getData()).build();
    final var messageMetadata = readMetadataFromBufferedContent(entry);
    IS_PROPAGATED_CHUNK_FROM_UPSTREAM_TIGER_PROXY.putValue(messageMetadata, true);
    messageElement.addFacet(messageMetadata);

    RbelMessageMetadata.PREVIOUS_MESSAGE_UUID.putValue(messageMetadata, entry.getPreviousUuid());

    messageElement.addFacet(
        RbelTcpIpMessageFacet.builder()
            .receiver(
                RbelMessageMetadata.MESSAGE_RECEIVER
                    .getValue(messageMetadata)
                    .map(h -> RbelHostnameFacet.buildRbelHostnameFacet(messageElement, h))
                    .orElse(RbelHostnameFacet.buildRbelHostnameFacet(messageElement, null)))
            .sender(
                RbelMessageMetadata.MESSAGE_SENDER
                    .getValue(messageMetadata)
                    .map(h -> RbelHostnameFacet.buildRbelHostnameFacet(messageElement, h))
                    .orElse(RbelHostnameFacet.buildRbelHostnameFacet(messageElement, null)))
            .sequenceNumber(rbelConverter.addMessageToHistoryWithNextSequenceNumber(messageElement))
            .build());
    log.atTrace()
        .addArgument(messageElement.getContent()::size)
        .addArgument(messageElement::getUuid)
        .addArgument(messageElement::getRawStringContent)
        .log("Propagating chunk with {} bytes and uuid {}: {}");
    rbelConverter.transmitElement(messageElement);
  }

  private synchronized List<RbelElement> parseAllAvailableMessages() {
    val result = new ArrayList<RbelElement>();
    while (!bufferedParts.isEmpty()) {
      val message = tryToConvertMessage();
      if (message.isPresent()) {
        lastMessageUuid = message.get().getUuid();
        bufferedParts.consume(message.get().getSize());
        result.add(message.get());
      } else {
        break;
      }
    }
    return result;
  }

  private Optional<RbelElement> tryToConvertMessage() {
    if (bufferedParts.isEmpty()) {
      return Optional.empty();
    }
    val bufferedContent = bufferedParts.peek();
    val originalSize = bufferedContent.getData().size();
    var messageElement =
        RbelElement.builder()
            .uuid(getOrGenerateUuid(bufferedContent))
            .content(bufferedContent.getData())
            .build();
    log.atTrace()
        .addArgument(bufferedContent::getUuid)
        .addArgument(messageElement::getUuid)
        .addArgument(bufferedContent.getConnectionIdentifier()::sender)
        .addArgument(bufferedContent.getConnectionIdentifier()::receiver)
        .addArgument(bufferedContent.getData()::size)
        .log(
            "Trying to convert message with base-uuid {} and given uuid {} from {} to {} with {}"
                + " bytes");
    Optional.ofNullable(bufferedContent.getMessagePreProcessor())
        .ifPresent(manipulator -> manipulator.accept(messageElement));
    final var messageMetadata = readMetadataFromBufferedContent(bufferedContent);
    messageElement.addFacet(
        new SingleConnectionParserMarkerFacet(bufferedContent.getSourceUuids()));
    log.atTrace()
        .addArgument(messageElement.getContent()::size)
        .addArgument(messageElement::getUuid)
        .log("Trying to parse message with {} bytes and uuid {}");
    final var result = triggerActualMessageParsing(messageElement, messageMetadata);
    messageElement.removeFacetsOfType(SingleConnectionParserMarkerFacet.class);
    if (result.getConversionPhase() == RbelConversionPhase.DELETED) {
      log.atTrace()
          .addArgument(() -> bufferedContent.getData().size())
          .addArgument(bufferedContent.getConnectionIdentifier()::printDirectionSymbol)
          .log("Tried parsing for buffered content with {} bytes, FAILED with direction {}");
      return Optional.empty();
    }
    log.atTrace()
        .addArgument(() -> bufferedContent.getData().size())
        .addArgument(bufferedContent.getConnectionIdentifier()::printDirectionSymbol)
        .addArgument(result::getUuid)
        .addArgument(() -> RbelConverterPlugin.messageIsCompleteOrParsingDeactivated(result))
        .log(
            "Tried parsing for buffered content with {} bytes, SUCCESS with direction {} and UUID"
                + " {}. Is complete: {}");

    bundledServerNamesAdder.addBundledServerNameToHostnameFacet(result);
    if (!rbelConverter.isActivateRbelParsing()) {
      result.addOrReplaceFacet(new UnparsedChunkFacet());
    }
    log.atTrace()
        .addArgument(originalSize)
        .addArgument(result::getSize)
        .log("parsed one message with {} bytes and {} used");
    return Optional.of(result).filter(RbelConverterPlugin::messageIsCompleteOrParsingDeactivated);
  }

  public RbelElement triggerActualMessageParsing(
      RbelElement messageElement, RbelMessageMetadata messageMetadata) {
    return rbelConverter.parseMessage(messageElement, messageMetadata);
  }

  private String getOrGenerateUuid(TcpConnectionEntry bufferedContent) {
    if (bufferedContent.getPositionInBaseNode() > 0
        || Boolean.TRUE.equals(
            bufferedContent
                .getAdditionalData()
                .getOrDefault(IS_PROPAGATED_CHUNK_FROM_UPSTREAM_TIGER_PROXY.getKey(), false))) {
      return DeterministicUuidGenerator.generateUuid(
          bufferedContent.getUuid(), bufferedContent.getPositionInBaseNode());
    } else {
      return bufferedContent.getUuid();
    }
  }

  private static RbelMessageMetadata readMetadataFromBufferedContent(
      TcpConnectionEntry bufferedContent) {
    final RbelMessageMetadata metadata =
        new RbelMessageMetadata()
            .withSender(RbelHostname.create(bufferedContent.getConnectionIdentifier().sender()))
            .withReceiver(
                RbelHostname.create(bufferedContent.getConnectionIdentifier().receiver()));
    bufferedContent.getAdditionalData().forEach(metadata::addMetadata);
    return metadata;
  }

  @Value
  public static class SingleConnectionParserMarkerFacet implements RbelFacet {
    List<String> sourceUuids;
  }

  public static class SingleConnectionParserUnparsedMessageRemoverPlugin
      extends RbelConverterPlugin {

    @Override
    public RbelConversionPhase getPhase() {
      return RbelConversionPhase.CONTENT_ENRICHMENT;
    }

    @Override
    public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
      if (rbelElement.hasFacet(SingleConnectionParserMarkerFacet.class)
          && converter.isActivateRbelParsing()
          && !messageIsCompleteOrParsingDeactivated(rbelElement)) {
        converter.removeMessage(rbelElement);
      }
    }
  }
}
