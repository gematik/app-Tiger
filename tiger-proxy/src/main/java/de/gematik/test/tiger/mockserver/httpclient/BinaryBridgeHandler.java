/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.httpclient;

import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.closeOnFlush;
import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.connectionClosedException;

import de.gematik.test.tiger.mockserver.configuration.Configuration;
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

  public BinaryBridgeHandler(Configuration configuration) {
    this.binaryProxyListener = configuration.binaryProxyListener();
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, BinaryMessage msg) throws Exception {
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
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    ctx.close();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
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
