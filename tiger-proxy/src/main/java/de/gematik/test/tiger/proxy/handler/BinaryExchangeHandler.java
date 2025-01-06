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

package de.gematik.test.tiger.proxy.handler;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelElementConvertionPair;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelBinaryFacet;
import de.gematik.rbellogger.data.facet.RbelFacet;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.facet.TigerNonPairedMessageFacet;
import de.gematik.rbellogger.data.facet.TracingMessagePairFacet;
import de.gematik.rbellogger.util.RbelContent;
import de.gematik.test.tiger.common.data.config.tigerproxy.DirectReverseProxyInfo;
import de.gematik.test.tiger.mockserver.model.BinaryMessage;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyRoutingException;
import de.gematik.test.tiger.proxy.exceptions.TigerRoutingErrorFacet;
import java.net.SocketAddress;
import java.net.SocketException;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Data
@Slf4j
public class BinaryExchangeHandler {

  private final TigerProxy tigerProxy;
  private final BinaryChunksBuffer binaryChunksBuffer;

  public BinaryExchangeHandler(TigerProxy tigerProxy) {
    this.tigerProxy = tigerProxy;
    this.binaryChunksBuffer =
        new BinaryChunksBuffer(
            tigerProxy.getRbelLogger().getRbelConverter(), tigerProxy.getTigerProxyConfiguration());
  }

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
                      convertedResponse ->
                          pairWithRequestAndPropagate(
                              convertedResponse, convertedRequest, shouldWaitForResponse))
                  .exceptionally(
                      t -> {
                        handleConversionException(t, clientAddress, serverAddress);
                        return null;
                      }));
    } catch (RuntimeException e) {
      log.warn("Uncaught exception during handling of request", e);
      propagateExceptionMessageSafe(
          e, RbelHostname.create(clientAddress), RbelHostname.create(serverAddress));
      throw e;
    }
  }

  private void pairWithRequestAndPropagate(
      Optional<RbelElement> convertedResponse,
      Optional<RbelElement> convertedRequest,
      boolean shouldWaitForResponse) {
    if (shouldWaitForResponse) {
      if (convertedResponse.isPresent() && convertedRequest.isPresent()) {
        var request = convertedRequest.get();
        var response = convertedResponse.get();
        final TracingMessagePairFacet pairFacet = new TracingMessagePairFacet(response, request);
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
  }

  private void handleConversionException(
      Throwable exception, SocketAddress clientAddress, SocketAddress serverAddress) {
    if (!shouldIgnoreConnectionErrors()) {
      if (isConnectionResetException(exception)) {
        log.trace("Connection reset:", exception);
      } else {
        log.warn("Exception during Direct-Proxy handling:", exception);
        propagateExceptionMessageSafe(
            exception, RbelHostname.create(clientAddress), RbelHostname.create(serverAddress));
      }
    }
  }

  private boolean shouldIgnoreConnectionErrors() {
    return Optional.ofNullable(getTigerProxy().getTigerProxyConfiguration().getDirectReverseProxy())
        .map(DirectReverseProxyInfo::isIgnoreConnectionErrors)
        .orElse(false);
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
        binaryChunksBuffer.tryToConvertMessageAndBufferUnusedBytes(
            RbelContent.of(message.getBytes()), senderAddress, receiverAddress);
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

  public void propagateExceptionMessageSafe(
      Throwable exception, RbelHostname senderAddress, RbelHostname receiverAddress) {
    try {
      tigerProxy.propagateException(exception);

      final TigerProxyRoutingException routingException =
          new TigerProxyRoutingException(
              "Exception during handling of HTTP request: " + exception.getMessage(),
              senderAddress,
              receiverAddress,
              exception);
      log.info(routingException.getMessage(), routingException);

      val message = new RbelElement(new byte[] {}, null);
      message.addFacet(new TigerRoutingErrorFacet(routingException));
      tigerProxy
          .getRbelLogger()
          .getRbelConverter()
          .parseMessage(
              new RbelElementConvertionPair(message),
              senderAddress,
              receiverAddress,
              Optional.of(routingException.getTimestamp()));
    } catch (Exception handlingException) {
      log.warn(
          "While propagating an exception another error occured (ignoring):", handlingException);
    }
  }
}
