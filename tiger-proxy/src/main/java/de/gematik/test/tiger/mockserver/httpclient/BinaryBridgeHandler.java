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

package de.gematik.test.tiger.mockserver.httpclient;

import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.closeOnFlush;
import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.connectionClosedException;

import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.model.BinaryMessage;
import de.gematik.test.tiger.mockserver.model.BinaryProxyListener;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

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
  private final BinaryProxyListener binaryProxyListener;

  public BinaryBridgeHandler(MockServerConfiguration configuration) {
    this.binaryProxyListener = configuration.binaryProxyListener();
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, BinaryMessage msg) {
    Optional.ofNullable(ctx.channel().attr(INCOMING_CHANNEL).get())
        .orElseThrow(() -> new IllegalStateException("Incoming channel is not set."))
        .writeAndFlush(Unpooled.copiedBuffer(msg.getBytes()));
    binaryProxyListener.onProxy(
        msg,
        Optional.empty(),
        ctx.channel().attr(INCOMING_CHANNEL).get().remoteAddress(),
        ctx.channel().remoteAddress());
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) {
    ctx.close();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    if (connectionClosedException(cause)) {
      log.error(
          "exception caught by {} handler -> closing pipeline {}",
          this.getClass(),
          ctx.channel(),
          cause);
    }
    closeOnFlush(ctx.channel());
  }
}
