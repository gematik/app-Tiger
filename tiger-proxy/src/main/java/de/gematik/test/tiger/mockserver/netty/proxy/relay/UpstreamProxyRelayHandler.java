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

package de.gematik.test.tiger.mockserver.netty.proxy.relay;

import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.closeOnFlush;
import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.connectionClosedException;
import static de.gematik.test.tiger.mockserver.netty.unification.PortUnificationHandler.isSslEnabledDownstream;
import static de.gematik.test.tiger.mockserver.socket.tls.SniHandler.getAlpnProtocol;

import de.gematik.test.tiger.mockserver.netty.MockServer;
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
  private final MockServer server;

  public UpstreamProxyRelayHandler(MockServer server, Channel upstreamChannel, Channel downstreamChannel) {
    super(false);
    this.server = server;
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
            server.getClientSslContextFactory()
                  .createClientSslContext(getAlpnProtocol(ctx))
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
