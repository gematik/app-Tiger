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

import de.gematik.rbellogger.data.RbelHostname;
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
import java.util.Optional;
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
  private final BinaryExchangeHandler binaryProxyListener;
  private final BinaryModifierApplier binaryModifierApplier;
  private final ChannelContextLogger contextLogger = new ChannelContextLogger(log);

  public BinaryBridgeHandler(MockServerConfiguration configuration) {
    this.binaryProxyListener = configuration.binaryProxyListener();
    this.binaryModifierApplier = new BinaryModifierApplier(configuration);
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, BinaryMessage msg) {
    for (val msgToSend : binaryModifierApplier.applyModifierPlugins(msg, ctx)) {
      Optional.ofNullable(ctx.channel().attr(INCOMING_CHANNEL).get())
          .orElseThrow(() -> new IllegalStateException("Incoming channel is not set."))
          .writeAndFlush(Unpooled.copiedBuffer(msgToSend.getBytes()));
      binaryProxyListener.onProxy(
          msgToSend,
          ctx.channel().attr(INCOMING_CHANNEL).get().remoteAddress(),
          ctx.channel().remoteAddress());
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) {
    contextLogger.logStage(ctx, "Outgoing channel of binary proxy is being closed");
    ctx.close();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    binaryProxyListener.propagateExceptionMessageSafe(
        cause,
        RbelHostname.create(ctx.channel().remoteAddress()),
        RbelHostname.create(ctx.channel().attr(INCOMING_CHANNEL).get().remoteAddress()));
    closeOnFlush(ctx.channel());
  }
}
