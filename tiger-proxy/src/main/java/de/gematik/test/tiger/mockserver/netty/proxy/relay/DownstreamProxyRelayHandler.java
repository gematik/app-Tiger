/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.netty.proxy.relay;

import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.closeOnFlush;
import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.connectionClosedException;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Slf4j
public class DownstreamProxyRelayHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

  private final Channel upstreamChannel;

  public DownstreamProxyRelayHandler(Channel upstreamChannel) {
    super(false);
    this.upstreamChannel = upstreamChannel;
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    ctx.read();
    ctx.write(Unpooled.EMPTY_BUFFER);
  }

  @Override
  public void channelRead0(final ChannelHandlerContext ctx, final FullHttpResponse response) {
    upstreamChannel
        .writeAndFlush(response)
        .addListener(
            (ChannelFutureListener)
                future -> {
                  if (future.isSuccess()) {
                    ctx.read();
                  } else {
                    if (isNotSocketClosedException(future.cause())) {
                      log.error("exception while returning writing {}", response, future.cause());
                    }
                    future.channel().close();
                  }
                });
  }

  private boolean isNotSocketClosedException(Throwable cause) {
    return !(cause instanceof ClosedChannelException || cause instanceof ClosedSelectorException);
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) {
    closeOnFlush(upstreamChannel);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    if (connectionClosedException(cause)) {
      log.error("exception caught by downstream relay handler -> closing pipeline {}", ctx.channel(), cause);
    }
    closeOnFlush(ctx.channel());
  }
}
