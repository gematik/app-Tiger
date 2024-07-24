/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.netty.proxy.relay;

import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.closeOnFlush;
import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.connectionClosedException;
import static de.gematik.test.tiger.mockserver.model.Protocol.HTTP_2;
import static de.gematik.test.tiger.mockserver.netty.unification.PortUnificationHandler.isSslEnabledDownstream;
import static de.gematik.test.tiger.mockserver.netty.unification.PortUnificationHandler.nettySslContextFactory;
import static de.gematik.test.tiger.mockserver.socket.tls.SniHandler.getALPNProtocol;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.ssl.SslHandler;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Slf4j
public class UpstreamProxyRelayHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

  private final Channel upstreamChannel;
  private final Channel downstreamChannel;

  public UpstreamProxyRelayHandler(Channel upstreamChannel, Channel downstreamChannel) {
    super(false);
    this.upstreamChannel = upstreamChannel;
    this.downstreamChannel = downstreamChannel;
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    ctx.read();
    ctx.write(Unpooled.EMPTY_BUFFER);
  }

  @Override
  public void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest request) {
    if (isSslEnabledDownstream(upstreamChannel)
        && downstreamChannel.pipeline().get(SslHandler.class) == null) {
      downstreamChannel
          .pipeline()
          .addFirst(
              nettySslContextFactory(ctx.channel())
                  .createClientSslContext(HTTP_2.equals(getALPNProtocol(ctx)))
                  .newHandler(ctx.alloc()));
    }
    downstreamChannel
        .writeAndFlush(request)
        .addListener(
            (ChannelFutureListener)
                future -> {
                  if (future.isSuccess()) {
                    ctx.channel().read();
                  } else {
                    if (isNotSocketClosedException(future.cause())) {
                      log.error(
                          "exception while returning response for request \"{} {}\"",
                          request.method(),
                          request.uri(),
                          future.cause());
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
    closeOnFlush(downstreamChannel);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    if (connectionClosedException(cause)) {
      log.error(
          "exception caught by upstream relay handler -> closing pipeline {}",
          ctx.channel(),
          cause);
    }
    closeOnFlush(ctx.channel());
  }
}
