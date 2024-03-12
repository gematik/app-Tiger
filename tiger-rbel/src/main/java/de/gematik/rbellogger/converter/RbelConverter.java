/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.key.RbelKeyManager;
import de.gematik.rbellogger.util.RbelMessagesDequeFacade;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PUBLIC)
@Slf4j
public class RbelConverter {

  static {
    Security.addProvider(new BouncyCastleProvider());
    Security.addProvider(new BouncyCastlePQCProvider());
  }

  private final Deque<RbelElement> messageHistory = new ConcurrentLinkedDeque<>();
  private final Set<String> knownMessageUuids = ConcurrentHashMap.newKeySet();
  private final RbelMultiMap<CompletableFuture<RbelElement>> messagesWaitingForCompletion =
      new RbelMultiMap<>();
  @Getter private final RbelKeyManager rbelKeyManager;
  @Getter private final RbelValueShader rbelValueShader = new RbelValueShader();
  @Getter private final List<RbelConverterPlugin> postConversionListeners = new ArrayList<>();
  private final List<RbelConverterPlugin> converterPlugins =
      new ArrayList<>(
          List.of(
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
              new RbelVauEpaKeyDeriver(),
              new RbelMtomConverter(),
              new RbelX509Converter(),
              new RbelX500Converter(),
              new RbelSicctEnvelopeConverter(),
              new RbelSicctCommandConverter(),
              new RbelCetpConverter(),
              new RbelCborConverter()));
  @Builder.Default private int rbelBufferSizeInMb = 1024;
  @Builder.Default private boolean manageBuffer = false;
  @Getter @Builder.Default private long currentBufferSize = 0;
  @Builder.Default private long messageSequenceNumber = 0;
  @Builder.Default private int skipParsingWhenMessageLargerThanKb = -1;

  public RbelElement convertElement(final byte[] input, RbelElement parentNode) {
    return convertElement(RbelElement.builder().parentNode(parentNode).rawContent(input).build());
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

  public RbelElement convertElement(final RbelElement convertedInput) {
    log.trace("Converting {}...", convertedInput);
    boolean elementIsOversized =
        skipParsingWhenMessageLargerThanKb > -1
            && (convertedInput.getRawContent().length > skipParsingWhenMessageLargerThanKb * 1024);
    for (RbelConverterPlugin plugin : converterPlugins) {
      if (!plugin.ignoreOversize() && elementIsOversized) {
        continue;
      }
      try {
        plugin.consumeElement(convertedInput, this);
      } catch (RuntimeException e) {
        final String msg =
          "Exception during conversion with plugin '"
          + plugin.getClass().getName()
          + "' ("
          + e.getMessage()
          + ")\n"
          + ExceptionUtils.getStackTrace(e);
        log.info(msg, e);
        if (log.isDebugEnabled()) {
          log.debug(
              "Content in failed conversion-attempt was (B64-encoded) {}",
              Base64.getEncoder().encodeToString(convertedInput.getRawContent()));
          if (convertedInput.getParentNode() != null) {
            log.debug(
                "Parent-Content in failed conversion-attempt was (B64-encoded) {}",
                Base64.getEncoder().encodeToString(convertedInput.getParentNode().getRawContent()));
          }
        }
        convertedInput.addFacet(new RbelNoteFacet(msg, RbelNoteFacet.NoteStyling.ERROR));
      }
    }
    return convertedInput;
  }

  private Optional<RbelElement> findLastRequest() {
    for (var iterator = messageHistory.descendingIterator(); iterator.hasNext(); ) {
      var element = iterator.next();
      if (element.hasFacet(RbelHttpRequestFacet.class)) {
        return Optional.of(element);
      }
    }
    return Optional.empty();
  }

  public void registerListener(final RbelConverterPlugin listener) {
    postConversionListeners.add(listener);
  }

  public void triggerPostConversionListenerFor(RbelElement element) {
    for (RbelConverterPlugin postConversionListener : postConversionListeners) {
      postConversionListener.consumeElement(element, this);
    }
  }

  public void addConverter(RbelConverterPlugin converter) {
    converterPlugins.add(converter);
  }

  public RbelElement parseMessage(
      byte[] content,
      RbelHostname sender,
      RbelHostname receiver,
      Optional<ZonedDateTime> transmissionTime) {
    final RbelElement messageElement = RbelElement.builder().rawContent(content).build();
    return parseMessage(messageElement, sender, receiver, transmissionTime);
  }

  public RbelElement parseMessage(
      @NonNull final RbelElement messageElement,
      final RbelHostname sender,
      final RbelHostname receiver,
      final Optional<ZonedDateTime> transmissionTime) {
    try {
      return parseMessageAsync(messageElement, sender, receiver, transmissionTime).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RbelConversionException(e);
    } catch (ExecutionException e) {
      throw new RbelConversionException(e);
    }
  }

  public CompletableFuture<RbelElement> parseMessageAsync(
      @NonNull final RbelElement messageElement,
      final RbelHostname sender,
      final RbelHostname receiver,
      final Optional<ZonedDateTime> transmissionTime) {
    addMessageToHistory(messageElement);
    messageElement.addFacet(
        RbelTcpIpMessageFacet.builder()
            .receiver(RbelHostnameFacet.buildRbelHostnameFacet(messageElement, receiver))
            .sender(RbelHostnameFacet.buildRbelHostnameFacet(messageElement, sender))
            .sequenceNumber(messageSequenceNumber++)
            .build());

    messageElement.addFacet(new RbelParsingNotCompleteFacet(this));
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            convertElement(messageElement);
            doMessagePostConversion(messageElement, transmissionTime);
            return messageElement;
          } finally {
            messageElement.removeFacetsOfType(RbelParsingNotCompleteFacet.class);
          }
        });
  }

  public RbelElement doMessagePostConversion(
      @NonNull final RbelElement rbelElement, Optional<ZonedDateTime> transmissionTime) {
    if (rbelElement
        .getFacet(RbelHttpResponseFacet.class)
        .map(resp -> resp.getRequest() == null)
        .orElse(false)) {
      final Optional<RbelElement> request = findLastRequest();
      rbelElement.addOrReplaceFacet(
          rbelElement
              .getFacet(RbelHttpResponseFacet.class)
              .map(RbelHttpResponseFacet::toBuilder)
              .orElse(RbelHttpResponseFacet.builder())
              .request(request.orElse(null))
              .build());
      request
          .flatMap(req -> req.getFacet(RbelHttpRequestFacet.class))
          .ifPresent(
              reqFacet ->
                  request
                      .get()
                      .addOrReplaceFacet(reqFacet.toBuilder().response(rbelElement).build()));
    }

    transmissionTime.ifPresent(
        tt -> rbelElement.addFacet(RbelMessageTimingFacet.builder().transmissionTime(tt).build()));

    rbelElement.triggerPostConversionListener(this);
    return rbelElement;
  }

  private void addMessageToHistory(RbelElement rbelElement) {
    synchronized (messageHistory) {
      currentBufferSize += rbelElement.getSize();
      knownMessageUuids.add(rbelElement.getUuid());
      messageHistory.add(rbelElement);
    }
    manageRbelBufferSize();
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
        if (rbelBufferSizeInMb <= 0 && !messageHistory.isEmpty()) {
          currentBufferSize = 0;
          messageHistory.clear();
          knownMessageUuids.clear();
        }
        if (rbelBufferSizeInMb > 0) {
          long exceedingLimit = getExceedingLimit(currentBufferSize);
          if (exceedingLimit > 0) {
            log.trace(
                "Buffer is currently at {} Mb which exceeds the limit of {} Mb",
                currentBufferSize / (1024 ^ 2),
                rbelBufferSizeInMb);
          }
          while (exceedingLimit > 0 && !messageHistory.isEmpty()) {
            log.trace("Exceeded buffer size, dropping oldest message in history");
            final RbelElement messageToDrop = messageHistory.getFirst();
            exceedingLimit -= messageToDrop.getSize();
            currentBufferSize -= messageToDrop.getSize();
            knownMessageUuids.remove(messageToDrop.getUuid());
            messageHistory.removeLast();
          }
        }
      }
    }
  }

  private long getExceedingLimit(long messageHistorySize) {
    return messageHistorySize - ((long) rbelBufferSizeInMb * 1024 * 1024);
  }

  public boolean isMessageUuidAlreadyKnown(String msgUuid) {
    return knownMessageUuids.contains(msgUuid);
  }

  public Stream<RbelElement> messagesStreamLatestFirst() {
    return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(
            messageHistory.descendingIterator(), Spliterator.ORDERED),
        false);
  }

  /**
   * Returns a list of all fully parsed messages. This list does not include messages that are not
   * parsed yet. To guarantee consistent sequence numbers the list stops before the first unparsed
   * message.
   */
  public List<RbelElement> getMessageList() {
    synchronized (messageHistory) {
      return messageHistory.stream()
          .takeWhile(msg -> !msg.hasFacet(RbelParsingNotCompleteFacet.class))
          .toList();
    }
  }

  /**
   * Gives a view of the current messages. This view includes messages that are not yet fully
   * parsed.
   */
  public RbelMessagesDequeFacade getMessageHistoryAsync() {
    synchronized (messageHistory) {
      return new RbelMessagesDequeFacade(messageHistory, this);
    }
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

  public void waitForGivenElementToBeParsed(RbelElement result) {
    waitForGivenMessagesToBeParsed(List.of(result));
  }

  public void waitForAllElementsBeforeGivenToBeParsed(RbelElement element) {
    // Collect unfinished messages
    List<RbelElement> unfinishedMessages = new ArrayList<>();
    synchronized (messageHistory) {
      for (RbelElement msg : messageHistory) {
        if (msg == element || msg.getUuid().equals(element.getUuid())) {
          break;
        }
        if (msg.hasFacet(RbelParsingNotCompleteFacet.class)) {
          unfinishedMessages.add(msg);
        }
      }
    }
    waitForGivenMessagesToBeParsed(unfinishedMessages);
  }

  private void waitForGivenMessagesToBeParsed(List<RbelElement> unfinishedMessages) {
    // register callbacks
    final List<Pair<CompletableFuture<RbelElement>, RbelElement>> callbacks;
    synchronized (messagesWaitingForCompletion) {
      callbacks =
          unfinishedMessages.stream()
              .filter(msg -> msg.hasFacet(RbelParsingNotCompleteFacet.class))
              .map(
                  msg -> {
                    final CompletableFuture<RbelElement> future = new CompletableFuture<>();
                    messagesWaitingForCompletion.put(msg.getUuid(), future);
                    return Pair.of(future, msg);
                  })
              .toList();
    }
    // wait for completion
    for (Pair<CompletableFuture<RbelElement>, RbelElement> future : callbacks) {
      try {
        future.getKey().get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RbelConversionException(e);
      } catch (ExecutionException e) {
        throw new RbelConversionException(e);
      }
    }
  }

  public void signalMessageParsingIsComplete(RbelElement element) {
    final List<CompletableFuture<RbelElement>> completableFutures;
    synchronized (messagesWaitingForCompletion) {
      completableFutures = messagesWaitingForCompletion.removeAll(element.getUuid());
    }
    completableFutures.forEach(future -> future.complete(element));
  }
}
