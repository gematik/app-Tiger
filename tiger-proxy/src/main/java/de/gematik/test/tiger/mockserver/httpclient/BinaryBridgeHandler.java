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
package de.gematik.test.tiger.mockserver.httpclient;

import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.closeOnFlush;

import de.gematik.rbellogger.data.RbelMessageKind;
import de.gematik.rbellogger.util.RbelSocketAddress;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.logging.ChannelContextLogger;
import de.gematik.test.tiger.mockserver.model.BinaryMessage;
import de.gematik.test.tiger.mockserver.netty.proxy.BinaryModifierApplier;
import de.gematik.test.tiger.proxy.handler.BinaryExchangeHandler;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * When creating a direct reverse proxy, we want to keep two channels open and forward all messages
 * coming from the outgoing channel to the incoming channel.
 */
@Slf4j
public class BinaryBridgeHandler extends SimpleChannelInboundHandler<BinaryMessage> {
  public static final AttributeKey<Channel> OUTGOING_CHANNEL =
      AttributeKey.valueOf("OUTGOING_CHANNEL");
  public static final AttributeKey<Channel> INCOMING_CHANNEL =
      AttributeKey.valueOf("INCOMING_CHANNEL");
  public static final AttributeKey<RbelSocketAddress> VIRTUAL_SERVER_ADDRESS =
      AttributeKey.valueOf("VIRTUAL_SERVER_ADRESS");
  private final BinaryExchangeHandler binaryProxyListener;
  private final BinaryModifierApplier binaryModifierApplier;
  private final ChannelContextLogger contextLogger = new ChannelContextLogger(log);

  public BinaryBridgeHandler(MockServerConfiguration configuration) {
    this.binaryProxyListener = configuration.binaryProxyListener();
    this.binaryModifierApplier = new BinaryModifierApplier(configuration);
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, BinaryMessage msg) {
    /*
    The two steps, transmission and onProxy, have to be atomic: onProxy is determining the order of messages in the
    message log, transmission the physical order. These have to match. Since multiple instances of binaryBridgeHandler
    could be around we synchronize on the listener, which is unique per tigerProxy.
    The other location is in BinaryHandler.sendMessage
     */
    synchronized (binaryProxyListener) {
      for (val msgToSend :
          binaryModifierApplier.applyModifierPlugins(msg, ctx, RbelMessageKind.RESPONSE)) {
        final Channel incomingChannel = ctx.channel().attr(INCOMING_CHANNEL).get();
        if (incomingChannel == null) {
          throw new IllegalStateException("Incoming channel is not set.");
        }
        incomingChannel.writeAndFlush(Unpooled.copiedBuffer(msgToSend.getBytes()));
        val clientAddress = getVirtualServerAddress(ctx.channel(), incomingChannel);
        val serverAddress = RbelSocketAddress.create(incomingChannel.remoteAddress());
        binaryProxyListener.onProxy(
            msgToSend, serverAddress, clientAddress, RbelMessageKind.RESPONSE);
      }
    }
  }

  private static RbelSocketAddress getVirtualServerAddress(
      Channel outgoingChannel, Channel incomingChannel) {
    val virtualAddress = incomingChannel.attr(VIRTUAL_SERVER_ADDRESS).get();
    if (virtualAddress != null) {
      return virtualAddress;
    } else {
      return RbelSocketAddress.create(outgoingChannel.remoteAddress());
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) {
    contextLogger.logStage(ctx, "Outgoing channel of binary proxy is being closed");
    // Skip close if event loop is shutting down to avoid RejectedExecutionException
    if (!ctx.channel().eventLoop().isShuttingDown()) {
      ctx.close();
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    binaryProxyListener.propagateExceptionMessageSafe(
        cause,
        RbelSocketAddress.create(ctx.channel().remoteAddress()),
        RbelSocketAddress.create(ctx.channel().attr(INCOMING_CHANNEL).get().remoteAddress()));
    closeOnFlush(ctx.channel());
  }
}
