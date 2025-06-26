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
package de.gematik.test.tiger.mockserver.netty.proxy;

import static de.gematik.test.tiger.mockserver.httpclient.BinaryBridgeHandler.INCOMING_CHANNEL;
import static de.gematik.test.tiger.mockserver.httpclient.BinaryBridgeHandler.OUTGOING_CHANNEL;
import static de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler.REMOTE_SOCKET;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.core.RbelHostnameFacet;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.model.BinaryMessage;
import de.gematik.test.tiger.proxy.data.TcpIpConnectionIdentifier;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyException;
import de.gematik.test.tiger.proxy.handler.BinaryExchangeHandler;
import de.gematik.test.tiger.proxy.handler.MultipleBinaryConnectionParser;
import de.gematik.test.tiger.proxy.handler.RbelBinaryModifierPlugin;
import de.gematik.test.tiger.proxy.handler.SingleConnectionParser;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import java.net.SocketAddress;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Applies all registered binary modifier plugins to a message. The plugins are applied in the order
 * in which they are registered.
 */
@Slf4j
public class BinaryModifierApplier {
  private static final ExecutorService executor = Executors.newCachedThreadPool();
  private final List<RbelBinaryModifierPlugin> binaryModifierPlugins;
  private final RbelConverter rbelConverter;
  private final MultipleBinaryConnectionParser multipleBinaryConnectionParser;

  public BinaryModifierApplier(MockServerConfiguration configuration) {
    this.binaryModifierPlugins = configuration.binaryModifierPlugins();
    this.rbelConverter = configuration.rbelConverter();
    this.multipleBinaryConnectionParser =
        new MultipleBinaryConnectionParser(
            conId ->
                new NonRememberingSingleConnectionParser(
                    conId,
                    executor,
                    configuration.rbelConverter(),
                    configuration.binaryProxyListener()));
  }

  /**
   * Applies all registered binary modifier plugins to the given message. The Applier will buffer
   * message parts until a complete message is received. Thus, the result may be empty, even if the
   * input is not. If no plugins are registered, the original message is returned unchanged.
   *
   * @param message the message to apply the plugins to
   * @param ctx the ChannelHandlerContext from which the message originates
   * @return a list of modified binary messages
   */
  public List<BinaryMessage> applyModifierPlugins(
      BinaryMessage message, ChannelHandlerContext ctx) {
    return applyModifierPluginsInternal(message, ctx);
  }

  private List<BinaryMessage> applyModifierPluginsInternal(
      BinaryMessage message, ChannelHandlerContext ctx) {
    if (binaryModifierPlugins == null || binaryModifierPlugins.isEmpty()) {
      return List.of(message);
    }
    return binaryMessageToRbelElement(message, ctx).stream()
        .map(target -> filterMessageThroughPlugins(message, target))
        .toList();
  }

  private BinaryMessage filterMessageThroughPlugins(BinaryMessage message, RbelElement target) {
    var modifiedMessage =
        new AtomicReference<>(new BinaryMessage(target.getRawContent(), message.getTimestamp()));
    var parsedMessage = new AtomicReference<>(target);
    for (RbelBinaryModifierPlugin plugin : binaryModifierPlugins) {
      try {
        plugin
            .modify(parsedMessage.get(), rbelConverter)
            .ifPresent(
                res -> {
                  modifiedMessage.set(new BinaryMessage(res, message.getTimestamp()));
                  parsedMessage.set(
                      rbelConverter.convertElement(
                          new RbelElement(res, null)
                              .addFacet(
                                  parsedMessage.get().getFacetOrFail(RbelMessageMetadata.class))));
                });
      } catch (Exception e) {
        log.warn(
            "Exception during modification of binary message with plugin {}: {}",
            plugin.getClass().getSimpleName(),
            e.getMessage());
      }
    }
    return modifiedMessage.get();
  }

  private List<RbelElement> binaryMessageToRbelElement(
      BinaryMessage message, ChannelHandlerContext ctx) {
    try {
      final Optional<SocketAddress> incoming =
          Optional.ofNullable(ctx.channel().attr(INCOMING_CHANNEL).get())
              .map(Channel::remoteAddress)
              .or(() -> Optional.ofNullable(ctx.channel().remoteAddress()));
      final Optional<SocketAddress> outgoing =
          Optional.ofNullable(ctx.channel().attr(REMOTE_SOCKET).get())
              .map(SocketAddress.class::cast)
              .or(
                  () ->
                      Optional.ofNullable(ctx.channel().attr(OUTGOING_CHANNEL).get())
                          .map(Channel::remoteAddress));
      return multipleBinaryConnectionParser
          .addToBuffer(
              incoming.orElse(null),
              outgoing.orElse(null),
              message.getBytes(),
              ZonedDateTime.of(message.getTimestamp(), ZoneId.systemDefault()))
          .get(10, TimeUnit.MINUTES);
    } catch (InterruptedException | TimeoutException e) {
      Thread.currentThread().interrupt();
      throw new TigerProxyException(
          "Could not complete waiting for message to be parsed before applying binary modifier!",
          e);
    } catch (ExecutionException e) {
      throw new TigerProxyException("Exception while converting message!", e);
    }
  }

  private class NonRememberingSingleConnectionParser extends SingleConnectionParser {
    public NonRememberingSingleConnectionParser(
        TcpIpConnectionIdentifier conId,
        ExecutorService executor,
        RbelConverter rbelConverter,
        BinaryExchangeHandler binaryExchangeHandler) {
      super(conId, executor, rbelConverter, binaryExchangeHandler);
    }

    @Override
    public RbelElement triggerActualMessageParsing(
        RbelElement messageElement, RbelMessageMetadata messageMetadata) {
      if (!messageElement.hasFacet(RbelTcpIpMessageFacet.class)) {
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
                .build());
      }
      messageElement.addFacet(messageMetadata);
      val result = rbelConverter.convertElement(messageElement);
      result
          .getFacet(TracingMessagePairFacet.class)
          .ifPresent(
              pairFacet -> {
                pairFacet.getRequest().removeFacetsOfType(TracingMessagePairFacet.class);
                pairFacet.getResponse().removeFacetsOfType(TracingMessagePairFacet.class);
              });
      return result;
    }
  }
}
