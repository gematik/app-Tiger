/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.handler;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelElementConvertionPair;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelBinaryFacet;
import de.gematik.rbellogger.data.facet.RbelFacet;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.rbellogger.data.facet.RbelNoteFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.facet.TigerNonPairedMessageFacet;
import de.gematik.rbellogger.data.facet.TracingMessagePairFacet;
import de.gematik.rbellogger.util.RbelException;
import de.gematik.test.tiger.mockserver.model.BinaryMessage;
import de.gematik.test.tiger.mockserver.model.BinaryProxyListener;
import de.gematik.test.tiger.proxy.TigerProxy;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.Arrays;

@Data
@AllArgsConstructor
@Slf4j
public class BinaryExchangeHandler implements BinaryProxyListener {

  private final BundledServerNamesAdder bundledServerNamesAdder = new BundledServerNamesAdder();
  private final TigerProxy tigerProxy;
  private final Map<Pair<SocketAddress, SocketAddress>, byte[]> bufferedParts =
      new ConcurrentHashMap<>();
  private final Map<Pair<SocketAddress, SocketAddress>, Long> currentSequenceNumber =
      new ConcurrentHashMap<>();

  @Override
  public void onProxy(
      BinaryMessage binaryRequest,
      Optional<CompletableFuture<BinaryMessage>> binaryResponseFuture,
      SocketAddress serverAddress,
      SocketAddress clientAddress) {
    try {
      log.trace("Finalizing binary exchange...");
      var convertedRequest =
          convertBinaryMessageOrPushToBuffer(binaryRequest, clientAddress, serverAddress);
      boolean shouldWaitForResponse = shouldWaitForResponse(convertedRequest);
      log.trace("Converted request, waiting on response: {}", shouldWaitForResponse);
      if (!shouldWaitForResponse) {
        convertedRequest.ifPresent(
            msg -> {
              msg.addFacet(new TigerNonPairedMessageFacet());
              getTigerProxy().triggerListener(msg);
            });
      }
      binaryResponseFuture.ifPresent(
          f ->
              f.thenApply(
                      binaryResponse ->
                          convertBinaryMessageOrPushToBuffer(
                              binaryResponse, serverAddress, clientAddress))
                  .thenAccept(
                      convertedResponse -> {
                        if (shouldWaitForResponse) {
                          if (convertedResponse.isPresent() && convertedRequest.isPresent()) {
                            var request = convertedRequest.get();
                            var response = convertedResponse.get();
                            final TracingMessagePairFacet pairFacet =
                                new TracingMessagePairFacet(response, request);
                            request.addOrReplaceFacet(pairFacet);
                            response.addOrReplaceFacet(pairFacet);
                            getTigerProxy().triggerListener(request);
                            getTigerProxy().triggerListener(response);
                          } else {
                            convertedRequest
                                .or(() -> convertedResponse)
                                .ifPresent(
                                    msg -> {
                                      msg.addOrReplaceFacet(new TigerNonPairedMessageFacet());
                                      getTigerProxy().triggerListener(msg);
                                    });
                          }
                        } else {
                          convertedResponse.ifPresent(
                              msg -> {
                                msg.addOrReplaceFacet(new TigerNonPairedMessageFacet());
                                getTigerProxy().triggerListener(msg);
                              });
                        }
                      })
                  .exceptionally(
                      t -> {
                        if (isConnectionResetException(t)) {
                          log.trace("Connection reset:", t);
                        } else {
                          log.warn("Exception during Direct-Proxy handling:", t);
                          propagateExceptionMessageSafe(t);
                        }
                        return null;
                      }));
      log.trace("Returning from BinaryExchangeHandler!");
    } catch (RuntimeException e) {
      log.warn("Uncaught exception during handling of request", e);
      propagateExceptionMessageSafe(e);
      throw e;
    }
  }

  private static boolean isConnectionResetException(Throwable t) {
    return TigerExceptionUtils.getCauseWithType(t, SocketException.class)
        .filter(e -> "Connection reset".equals(e.getMessage()))
        .isPresent();
  }

  private boolean shouldWaitForResponse(Optional<RbelElement> convertedRequest) {
    return convertedRequest.map(RbelElement::getFacets).stream()
        .flatMap(Queue::stream)
        .anyMatch(RbelFacet::shouldExpectReplyMessage);
  }

  private Optional<RbelElement> convertBinaryMessageOrPushToBuffer(
      BinaryMessage message, SocketAddress senderAddress, SocketAddress receiverAddress) {
    if (message == null) {
      return Optional.empty();
    }
    Optional<RbelElement> rbelMessageOptional =
        tryToConvertMessageAndBufferUnusedBytes(message, senderAddress, receiverAddress);
    if (rbelMessageOptional.isEmpty()) {
      return Optional.empty();
    }
    rbelMessageOptional
        .get()
        .addFacet(
            RbelMessageTimingFacet.builder()
                .transmissionTime(message.getTimestamp().atZone(ZoneId.systemDefault()))
                .build());
    rbelMessageOptional.get().addFacet(new RbelBinaryFacet());
    log.debug(
        "Finalized binary exchange {}",
        rbelMessageOptional
            .flatMap(msg -> msg.getFacet(RbelTcpIpMessageFacet.class))
            .map(RbelTcpIpMessageFacet::getSenderHostname)
            .map(Objects::toString)
            .orElse(""));
    return rbelMessageOptional;
  }

  private Optional<RbelElement> tryToConvertMessageAndBufferUnusedBytes(
      BinaryMessage message, SocketAddress senderAddress, SocketAddress receiverAddress) {
    var key = Pair.of(senderAddress, receiverAddress);
    final Optional<RbelElement> requestOptional =
        tryToConvertMessage(addBufferToMessage(message, key), senderAddress, receiverAddress, key);
    if (requestOptional.isPresent()) {
      bufferedParts.remove(key);
      currentSequenceNumber.remove(key);
    }
    return requestOptional;
  }

  private byte[] addBufferToMessage(BinaryMessage message, Pair<SocketAddress, SocketAddress> key) {
    byte[] bufferedBytes = bufferedParts.get(key);
    var resultMessage = message.getBytes();
    if (bufferedBytes != null) {
      resultMessage = Arrays.concatenate(bufferedBytes, resultMessage);
    }
    bufferedParts.put(key, resultMessage);
    return resultMessage;
  }

  private Optional<RbelElement> tryToConvertMessage(
      byte[] messageContent,
      SocketAddress senderAddress,
      SocketAddress receiverAddress,
      Pair<SocketAddress, SocketAddress> connectionKey) {
    var messageElement = new RbelElement(messageContent, null);
    final RbelElement result =
        getTigerProxy()
            .getRbelLogger()
            .getRbelConverter()
            .parseMessage(
                new RbelElementConvertionPair(messageElement),
                toRbelHostname(senderAddress),
                toRbelHostname(receiverAddress),
                Optional.empty(),
                Optional.ofNullable(currentSequenceNumber.get(connectionKey)));
    if (result.getFacets().stream().filter(f -> !(f instanceof RbelNoteFacet)).count() <= 1) {
      var sequenceNumber =
          result
              .getFacet(RbelTcpIpMessageFacet.class)
              .orElseThrow(() -> new RbelException("cannot retrieve sequence number"))
              .getSequenceNumber();
      currentSequenceNumber.put(connectionKey, sequenceNumber);
      getTigerProxy().getRbelLogger().getRbelConverter().removeMessage(result);
      return Optional.empty();
    }
    bundledServerNamesAdder.addBundledServerNameToHostnameFacet(result);
    return Optional.of(result);
  }

  private void propagateExceptionMessageSafe(Throwable exception) {
    try {
      tigerProxy.propagateException(exception);
    } catch (Exception handlingException) {
      log.warn(
          "While propagating an exception another error occured (ignoring):", handlingException);
    }
  }

  private RbelHostname toRbelHostname(SocketAddress socketAddress) {
    if (socketAddress instanceof InetSocketAddress inetSocketAddress) {
      return RbelHostname.builder()
          .hostname(inetSocketAddress.getHostName())
          .port(inetSocketAddress.getPort())
          .build();
    } else {
      log.warn(
          "Incompatible socketAddress encountered: " + socketAddress.getClass().getSimpleName());
      return null;
    }
  }
}
