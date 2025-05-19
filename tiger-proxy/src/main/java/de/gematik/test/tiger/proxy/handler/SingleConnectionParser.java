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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.val;
import org.apache.commons.lang3.StringUtils;

@RequiredArgsConstructor
public class SingleConnectionParser {
  public static final RbelMetadataValue<Boolean> IS_UNPARSED_MESSAGE_CHUNK =
      new RbelMetadataValue<>("isUnparsedMessageChunk", Boolean.class);

  private final BundledServerNamesAdder bundledServerNamesAdder = new BundledServerNamesAdder();

  private final AbstractTigerProxy tigerProxy;
  private final BinaryExchangeHandler binaryExchangeHandler;
  private final RbelConverter rbelConverter;
  private final AsyncByteQueue bufferedParts;
  private final org.slf4j.Logger log;

  private String lastMessageUuid = null;

  public SingleConnectionParser(
      TcpIpConnectionIdentifier connectionIdentifier,
      AbstractTigerProxy tigerProxy,
      BinaryExchangeHandler binaryExchangeHandler) {
    this.tigerProxy = tigerProxy;
    this.binaryExchangeHandler = binaryExchangeHandler;
    this.rbelConverter = tigerProxy.getRbelLogger().getRbelConverter();
    this.bufferedParts = new AsyncByteQueue(connectionIdentifier);
    log =
        org.slf4j.LoggerFactory.getLogger(
            tigerProxy.getName().orElse("TigerProxy") + "-ConnectionParser");
  }

  private Void handleException(
      Throwable throwable, TcpIpConnectionIdentifier direction, TcpConnectionEntry entry) {
    log.warn("Exception while parsing buffered content for message {}", entry.getUuid(), throwable);
    binaryExchangeHandler.propagateExceptionMessageSafe(
        throwable,
        RbelHostname.create(direction.sender()),
        RbelHostname.create(direction.receiver()));
    return null;
  }

  public void bufferNewPart(TcpConnectionEntry entry) {
    val bufferedEntry = bufferedParts.write(entry);
    log.atTrace()
        .addArgument(entry.getData()::size)
        .addArgument(tigerProxy::proxyName)
        .addArgument(bufferedEntry::getUuid)
        .addArgument(bufferedEntry::getPreviousUuid)
        .addArgument(() -> StringUtils.abbreviate(entry.getData().toReadableString(), 20))
        .addArgument(entry::getUuid)
        .addArgument(entry::getPreviousUuid)
        .log(
            "Buffering new content with {} bytes, in {} with uuid {} and prevUuid {} and first line"
                + " '{}' (original {} and prev {})");
    CompletableFuture.runAsync(() -> propagateNewChunk(bufferedEntry), tigerProxy.getExecutor())
        .thenRunAsync(this::parseAllAvailableMessages, tigerProxy.getExecutor())
        .exceptionally(e -> handleException(e, bufferedEntry.getConnectionIdentifier(), entry));
  }

  private void propagateNewChunk(TcpConnectionEntry entry) {
    if (Boolean.FALSE.equals(
        entry.getAdditionalData().getOrDefault(IS_UNPARSED_MESSAGE_CHUNK.getKey(), false))) {
      log.atTrace()
          .addArgument(entry::getUuid)
          .log("Skipping propagation of unparsed message chunk {}");
      return;
    }
    var messageElement =
        RbelElement.builder().uuid(entry.getUuid()).content(entry.getData()).build();
    final var messageMetadata = generateMetadataFromBufferedContent(entry);
    IS_UNPARSED_MESSAGE_CHUNK.putValue(messageMetadata, true);
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

  private synchronized void parseAllAvailableMessages() {
    while (!bufferedParts.isEmpty()) {
      val message = tryToConvertMessage();
      if (message.isPresent()) {
        lastMessageUuid = message.get().getUuid();
        bufferedParts.consume(message.get().getSize());
      } else {
        break;
      }
    }
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
    final var messageMetadata = generateMetadataFromBufferedContent(bufferedContent);
    if (lastMessageUuid != null) {
      RbelMessageMetadata.PREVIOUS_MESSAGE_UUID.putValue(messageMetadata, lastMessageUuid);
      log.atTrace()
          .addArgument(messageElement::getUuid)
          .addArgument(lastMessageUuid)
          .log("Setting previous message uuid of {} to {}");
    }
    messageElement.addFacet(
        new SingleConnectionParserMarkerFacet(bufferedContent.getSourceUuids()));
    log.atTrace()
        .addArgument(messageElement.getContent()::size)
        .addArgument(messageElement::getUuid)
        .log("Trying to parse message with {} bytes and uuid {}");
    val result = rbelConverter.parseMessage(messageElement, messageMetadata);
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
    if (!tigerProxy.getTigerProxyConfiguration().isActivateRbelParsing()) {
      result.addOrReplaceFacet(new UnparsedChunkFacet());
    }
    log.atTrace()
        .addArgument(originalSize)
        .addArgument(result::getSize)
        .log("parsed one message with {} bytes and {} used");
    return Optional.of(result).filter(RbelConverterPlugin::messageIsCompleteOrParsingDeactivated);
  }

  private static String getOrGenerateUuid(TcpConnectionEntry bufferedContent) {
    if (Boolean.FALSE.equals(
        bufferedContent
            .getAdditionalData()
            .getOrDefault(IS_UNPARSED_MESSAGE_CHUNK.getKey(), false))) {
      return bufferedContent.getUuid();
    } else {
      return DeterministicUuidGenerator.generateUuid(
          bufferedContent.getUuid(), bufferedContent.getPositionInBaseNode());
    }
  }

  private static RbelMessageMetadata generateMetadataFromBufferedContent(
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
