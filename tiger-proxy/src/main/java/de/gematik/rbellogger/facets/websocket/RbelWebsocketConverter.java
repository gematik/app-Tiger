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
package de.gematik.rbellogger.facets.websocket;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelMapFacet;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.data.core.RbelResponseFacet;
import de.gematik.rbellogger.data.core.RbelSocketAddressFacet;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.facets.http.RbelHttpMessageFacet;
import de.gematik.rbellogger.facets.http.RbelHttpRequestConverter;
import de.gematik.rbellogger.facets.http.RbelHttpResponseConverter;
import de.gematik.rbellogger.facets.http.RbelHttpResponseFacet;
import de.gematik.rbellogger.util.RbelSocketAddress;
import de.gematik.test.tiger.common.util.TcpIpConnectionIdentifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.Inflater;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
@ConverterInfo(
    dependsOn = {
      RbelHttpResponseConverter.class,
      RbelHttpRequestConverter.class,
      RbelWebsocketHandshakeConverter.class
    },
    onlyActivateFor = "websocket")
public class RbelWebsocketConverter extends RbelConverterPlugin {

  private static final long TTL_MS = 15L * 60 * 1000; // 15 minutes TTL for unused sessions
  private static final int CLEANUP_THRESHOLD = 500; // cleanup every 500 entries
  public static final String WEBSOCKET_LABEL = "Websocket";

  private final Map<TcpIpConnectionIdentifier, WebsocketSessionMetadata> metadataMap =
      Collections.synchronizedMap(new HashMap<>());

  // ...existing code...
  private final Map<String, Set<TcpIpConnectionIdentifier>> addressToConnectionIndex =
      Collections.synchronizedMap(new HashMap<>());

  private int callsSinceLastCleanup = 0;

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.PROTOCOL_PARSING;
  }

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    // only root TCP/IP messages are considered for websocket conversion
    if (rbelElement.getParentNode() != null
        || !rbelElement.hasFacet(RbelTcpIpMessageFacet.class)
        || rbelElement.hasFacet(RbelWebsocketHandshakeFacet.class)
        || rbelElement.hasFacet(RbelHttpMessageFacet.class)
        || !looksLikeWebsocketFrame(rbelElement)) {
      return;
    }

    findMetadata(rbelElement, converter)
        .ifPresent(
            metadata -> {
              try {
                applyLogicalFrameEndpoints(rbelElement, metadata);
                final Inflater usedInflater = determineUsedInflater(rbelElement, metadata);

                new RbelWebsocketMessageConverter(
                        rbelElement,
                        converter,
                        metadata.extensions,
                        usedInflater,
                        metadata.originalClient)
                    .parseWebsocketMessage();

                if (isCloseFrame(rbelElement)) {
                  handleCloseFrame(rbelElement, converter);
                }
              } catch (RuntimeException e) {
                log.error("Error while parsing websocket message", e);
              }
            });
  }

  private static boolean isCloseFrame(RbelElement rbelElement) {
    return rbelElement
        .getFacet(RbelWebsocketMessageFacet.class)
        .map(RbelWebsocketMessageFacet::getFrameType)
        .flatMap(frameTypeEl -> frameTypeEl.seekValue(RbelWebsocketFrameType.class))
        .filter(frameType -> frameType == RbelWebsocketFrameType.CLOSE_FRAME)
        .isPresent();
  }

  private static Inflater determineUsedInflater(
      RbelElement rbelElement, WebsocketSessionMetadata metadata) {
    final boolean fromClient =
        rbelElement
            .getFacet(RbelTcpIpMessageFacet.class)
            .flatMap(RbelTcpIpMessageFacet::getSenderHostname)
            .map(metadata.originalClient::isSameAddress)
            .orElse(false);

    return fromClient ? metadata.inflaterFromClient : metadata.inflaterFromServer;
  }

  private Optional<WebsocketSessionMetadata> findMetadata(
      RbelElement rbelElement, RbelConversionExecutor converter) {
    val connectionIdentifier =
        rbelElement
            .getFacet(RbelTcpIpMessageFacet.class)
            .map(RbelTcpIpMessageFacet::getTcpIpConnectionIdentifier)
            .orElse(null);
    val frameEndpoints = getFrameEndpoints(rbelElement);

    Optional<WebsocketSessionMetadata> cachedMetadata =
        Optional.ofNullable(connectionIdentifier).map(metadataMap::get);

    final var previousMessage =
        cachedMetadata
            .flatMap(m -> m.previousMessage)
            .or(() -> getPreviousMessage(rbelElement, converter))
            .filter(
                prevMessage ->
                    prevMessage.hasFacet(RbelWebsocketMessageFacet.class)
                        || prevMessage.hasFacet(RbelWebsocketHandshakeFacet.class));

    previousMessage.ifPresent(element -> setPreviousMessageMetadata(rbelElement, element));

    if (cachedMetadata.isPresent()) {
      cachedMetadata.get().previousMessage = Optional.of(rbelElement);
      return cachedMetadata;
    }

    if (previousMessage.isEmpty() && connectionIdentifier != null) {
      val sessionFromSibling = findSessionBySiblingAddress(frameEndpoints, connectionIdentifier);
      if (sessionFromSibling.isPresent()) {
        val metadata =
            new WebsocketSessionMetadata(
                sessionFromSibling.get().originalClient,
                sessionFromSibling.get().extensions,
                Optional.of(rbelElement));
        metadataMap.put(connectionIdentifier, metadata);
        indexAddress(connectionIdentifier, sessionFromSibling.get().originalClient);
        cleanupExpiredSessions();
        return Optional.of(metadata);
      }
    }

    if (previousMessage.isEmpty()) {
      val matchingHandshake = findCrossHopHandshake(rbelElement, converter);
      if (matchingHandshake.isPresent()) {
        setPreviousMessageMetadata(rbelElement, matchingHandshake.get());
        return Optional.of(
            cacheMetadata(connectionIdentifier, rbelElement, matchingHandshake.get()));
      }
      return Optional.empty();
    }

    val metadata = cacheMetadata(connectionIdentifier, rbelElement, previousMessage.get());
    return Optional.of(metadata);
  }

  private WebsocketSessionMetadata cacheMetadata(
      TcpIpConnectionIdentifier connectionIdentifier,
      RbelElement currentMessage,
      RbelElement previousMessage) {
    val originalClient = determineOriginalClient(previousMessage);
    val metadata =
        new WebsocketSessionMetadata(
            originalClient, extractExtensionMap(previousMessage), Optional.of(currentMessage));

    previousMessage
        .getFacet(RbelTcpIpMessageFacet.class)
        .flatMap(
            facet -> {
              if (previousMessage.hasFacet(RbelHttpResponseFacet.class)) {
                return facet.getSenderHostname();
              }
              return facet.getReceiverHostname();
            })
        .ifPresent(server -> metadata.logicalServer = Optional.of(server));

    if (connectionIdentifier != null) {
      metadataMap.put(connectionIdentifier, metadata);
      indexAddress(connectionIdentifier, originalClient);
      log.debug(
          "Cached WS session metadata: connectionId={}, originalClient={}, extensions={}",
          connectionIdentifier,
          originalClient,
          metadata.extensions.keySet());
    }
    return metadata;
  }

  private void indexAddress(
      TcpIpConnectionIdentifier connectionIdentifier, RbelSocketAddress socketAddress) {
    if (socketAddress == null) {
      return;
    }
    val key = getAddressKey(socketAddress);
    addressToConnectionIndex
        .computeIfAbsent(key, k -> Collections.synchronizedSet(new HashSet<>()))
        .add(connectionIdentifier);
  }

  private Optional<WebsocketSessionMetadata> findSessionBySiblingAddress(
      List<RbelSocketAddress> frameEndpoints, TcpIpConnectionIdentifier connectionIdentifier) {
    return frameEndpoints.stream()
        .map(this::getAddressKey)
        .map(addressToConnectionIndex::get)
        .filter(Objects::nonNull)
        .flatMap(Set::stream)
        .filter(connId -> !connId.equals(connectionIdentifier))
        .map(metadataMap::get)
        .filter(Objects::nonNull)
        .findFirst();
  }

  private String getAddressKey(RbelSocketAddress address) {
    return address.printHostname().toLowerCase() + ":" + address.getPort();
  }

  private void cleanupExpiredSessions() {
    if (++callsSinceLastCleanup < CLEANUP_THRESHOLD) {
      return;
    }
    callsSinceLastCleanup = 0;

    val now = System.currentTimeMillis();
    metadataMap
        .entrySet()
        .removeIf(
            entry -> {
              if (now - entry.getValue().lastAccessTime > TTL_MS) {
                removeFromIndex(entry.getKey(), entry.getValue());
                log.atDebug()
                    .addArgument(entry::getKey)
                    .log("Cleaned up expired websocket session: {}");
                return true;
              }
              return false;
            });
  }

  private void removeFromIndex(
      TcpIpConnectionIdentifier connectionId, WebsocketSessionMetadata metadata) {
    val key = getAddressKey(metadata.originalClient);
    val connections = addressToConnectionIndex.get(key);
    if (connections != null) {
      connections.remove(connectionId);
      if (connections.isEmpty()) {
        addressToConnectionIndex.remove(key);
      }
    }
  }

  private Optional<RbelElement> findCrossHopHandshake(
      RbelElement frameMessage, RbelConversionExecutor converter) {
    val frameConnectionIdentifier =
        frameMessage
            .getFacet(RbelTcpIpMessageFacet.class)
            .map(RbelTcpIpMessageFacet::getTcpIpConnectionIdentifier)
            .orElse(null);
    val frameEndpoints = getFrameEndpoints(frameMessage);
    if (frameConnectionIdentifier == null || frameEndpoints.isEmpty()) {
      return Optional.empty();
    }

    converter.waitForAllElementsBeforeGivenToBeParsed(frameMessage.findRootElement());
    return converter
        .getConverter()
        .findPreviousMessage(
            frameMessage,
            message ->
                message.hasFacet(RbelWebsocketHandshakeFacet.class)
                    && isSuccessfulHandshakeResponse(message)
                    && handshakeEndpointEqualsFrameEndpoint(message, frameEndpoints));
  }

  private boolean isSuccessfulHandshakeResponse(RbelElement message) {
    return message.hasFacet(RbelWebsocketHandshakeFacet.class);
  }

  private boolean handshakeEndpointEqualsFrameEndpoint(
      RbelElement handshakeMessage, List<RbelSocketAddress> frameEndpoints) {
    val handshakeEndpoints = getFrameEndpoints(handshakeMessage);
    return frameEndpoints.stream()
        .anyMatch(
            frameEp -> handshakeEndpoints.stream().anyMatch(hsEp -> equalAddresses(frameEp, hsEp)));
  }

  private List<RbelSocketAddress> getFrameEndpoints(RbelElement frameMessage) {
    return frameMessage.getFacet(RbelTcpIpMessageFacet.class).stream()
        .flatMap(
            facet ->
                Stream.of(
                    facet.getSenderHostname().orElse(null),
                    facet.getReceiverHostname().orElse(null)))
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  private RbelSocketAddress determineOriginalClient(RbelElement previousMessage) {
    return previousMessage
        .getFacet(RbelTcpIpMessageFacet.class)
        .flatMap(
            facet -> {
              if (previousMessage.hasFacet(RbelWebsocketHandshakeFacet.class)) {
                if (previousMessage.hasFacet(RbelHttpResponseFacet.class)) {
                  return Optional.ofNullable(facet.getReceiverAddress());
                } else {
                  return Optional.ofNullable(facet.getSenderAddress());
                }
              }
              return Optional.ofNullable(facet.getSenderAddress());
            })
        .orElseThrow();
  }

  private boolean equalAddresses(RbelSocketAddress left, RbelSocketAddress right) {
    if (left == null || right == null) {
      return false;
    }
    return left.isSameAddress(right);
  }

  private void setPreviousMessageMetadata(RbelElement rbelElement, RbelElement previousMessage) {
    rbelElement
        .getFacet(RbelMessageMetadata.class)
        .ifPresent(
            metadata -> {
              if (RbelMessageMetadata.PREVIOUS_MESSAGE_UUID.getValue(metadata).isEmpty()) {
                RbelMessageMetadata.PREVIOUS_MESSAGE_UUID.putValue(
                    metadata, previousMessage.getUuid());
                previousMessage
                    .getFacet(RbelMessageMetadata.class)
                    .flatMap(RbelMessageMetadata::getTransmissionTime)
                    .ifPresent(
                        time ->
                            RbelMessageMetadata.PREVIOUS_MESSAGE_TIMESTAMP.putValue(
                                metadata, time));
              }
            });
  }

  private RbelMultiMap<RbelElement> extractExtensionMap(RbelElement previousMessage) {
    return previousMessage
        .getFacet(RbelWebsocketHandshakeFacet.class)
        .map(RbelWebsocketHandshakeFacet::getExtensions)
        .or(
            () ->
                previousMessage
                    .getFacet(RbelWebsocketMessageFacet.class)
                    .map(RbelWebsocketMessageFacet::getExtensions))
        .flatMap(el -> el.getFacet(RbelMapFacet.class))
        .map(RbelMapFacet::getChildNodes)
        .orElseGet(RbelMultiMap::new);
  }

  private void applyLogicalFrameEndpoints(
      RbelElement rbelElement, WebsocketSessionMetadata metadata) {
    rbelElement
        .getFacet(RbelTcpIpMessageFacet.class)
        .ifPresent(tcpIpFacet -> classifyAndNormalizeFrame(rbelElement, tcpIpFacet, metadata));
  }

  private void classifyAndNormalizeFrame(
      RbelElement rbelElement,
      RbelTcpIpMessageFacet tcpIpFacet,
      WebsocketSessionMetadata metadata) {
    val currentSender = tcpIpFacet.getSenderHostname().orElse(null);
    val currentReceiver = tcpIpFacet.getReceiverHostname().orElse(null);
    if (currentSender == null || currentReceiver == null) {
      return;
    }

    final boolean isResponse = equalAddresses(currentReceiver, metadata.originalClient);
    final boolean isRequest = equalAddresses(currentSender, metadata.originalClient);

    if (isResponse) {
      rbelElement.addFacet(RbelResponseFacet.builder().menuInfoString(WEBSOCKET_LABEL).build());
    } else if (isRequest) {
      rbelElement.addFacet(new RbelRequestFacet(WEBSOCKET_LABEL, false));
    }
    if (!equalAddresses(currentSender, currentReceiver) && (isResponse || isRequest)) {
      val logicalSender = isResponse ? metadata.logicalServer.orElse(currentSender) : currentSender;
      val logicalReceiver =
          isResponse ? currentReceiver : metadata.logicalServer.orElse(currentReceiver);

      val normalizedFacet =
          tcpIpFacet.toBuilder()
              .sender(
                  RbelSocketAddressFacet.buildRbelSocketAddressFacet(rbelElement, logicalSender))
              .receiver(
                  RbelSocketAddressFacet.buildRbelSocketAddressFacet(rbelElement, logicalReceiver))
              .build();
      rbelElement.addOrReplaceFacet(normalizedFacet);
    }
  }

  private boolean looksLikeWebsocketFrame(RbelElement rbelElement) {
    val content = rbelElement.getContent();
    if (content.size() < 2) {
      return false;
    }

    val firstByte = content.get(0);
    val secondByte = content.get(1);

    // Extract opcode from first byte (bits 0-3)
    val opcode = firstByte & 0x0F;

    // Valid opcodes per RFC 6455:
    // 0x0 = continuation frame
    // 0x1 = text frame
    // 0x2 = binary frame
    // 0x3-0x7 = reserved (non-control)
    // 0x8 = close frame
    // 0x9 = ping frame
    // 0xA = pong frame
    // 0xB-0xF = reserved (control) - INVALID
    // Reject if opcode is in reserved range (0xB-0xF)
    if (opcode >= 0xB) {
      return false;
    }

    // Reject all-zero pattern which is highly unlikely to be a real frame
    return !(firstByte == 0 && secondByte == 0);
  }

  private void handleCloseFrame(RbelElement rbelElement, RbelConversionExecutor converter) {
    // Wait for all previous messages to be parsed before cleaning up the session
    converter.waitForAllElementsBeforeGivenToBeParsed(rbelElement.findRootElement());

    rbelElement
        .getFacet(RbelTcpIpMessageFacet.class)
        .map(RbelTcpIpMessageFacet::getTcpIpConnectionIdentifier)
        .ifPresent(this::removeSession);
  }

  private void removeSession(TcpIpConnectionIdentifier connectionIdentifier) {
    val removed = metadataMap.get(connectionIdentifier);
    if (removed == null) {
      return;
    }

    // Some servers/clients send two close frames (one per direction). To avoid
    // losing inflater/extension state due to a premature removal on the first
    // close frame, only remove the session on the second close. On the first
    // close we mark the session as closedOnce and keep it for a short grace
    // period so any remaining frames (or the mirror close) can still use the
    // inflater (important for permessage-deflate context takeover).
    if (!removed.closedOnce) {
      removed.closedOnce = true;
      removed.lastAccessTime = System.currentTimeMillis();
      log.debug(
          "Marked websocket session as closedOnce (deferring removal): {}", connectionIdentifier);
      return;
    }

    metadataMap.remove(connectionIdentifier);
    removeFromIndex(connectionIdentifier, removed);
    log.debug("Closed websocket session on close frame (final): {}", connectionIdentifier);
  }

  @RequiredArgsConstructor
  private class WebsocketSessionMetadata {
    private final RbelSocketAddress originalClient;
    private final Inflater inflaterFromClient = new Inflater(true);
    private final Inflater inflaterFromServer = new Inflater(true);
    private final RbelMultiMap<RbelElement> extensions;
    private Optional<RbelElement> previousMessage;
    private Optional<RbelSocketAddress> logicalServer = Optional.empty();
    private boolean closedOnce = false;
    private long lastAccessTime = System.currentTimeMillis();

    WebsocketSessionMetadata(
        RbelSocketAddress originalClient,
        RbelMultiMap<RbelElement> extensions,
        Optional<RbelElement> previousMessage) {
      this.originalClient = originalClient;
      this.extensions = extensions;
      this.previousMessage = previousMessage;
    }
  }
}
