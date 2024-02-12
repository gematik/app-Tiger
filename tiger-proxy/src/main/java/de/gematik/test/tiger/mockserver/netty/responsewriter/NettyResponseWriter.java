/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.mockserver.netty.responsewriter;


import de.gematik.test.tiger.mockserver.configuration.Configuration;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.HttpResponse;
import de.gematik.test.tiger.mockserver.responsewriter.ResponseWriter;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Slf4j
public class NettyResponseWriter extends ResponseWriter {

  private final ChannelHandlerContext ctx;

  public NettyResponseWriter(
      Configuration configuration,
      ChannelHandlerContext ctx) {
    super(configuration);
    this.ctx = ctx;
  }

  @Override
  public void sendResponse(HttpRequest request, HttpResponse response) {
    writeAndCloseSocket(ctx, request, response);
  }

  private void writeAndCloseSocket(
      final ChannelHandlerContext ctx, final HttpRequest request, HttpResponse response) {
    boolean closeChannel = !(request.getKeepAlive() != null && request.getKeepAlive());

    ChannelFuture channelFuture = ctx.writeAndFlush(response);
    if (closeChannel || configuration.alwaysCloseSocketConnections()) {
      channelFuture.addListener(
          (ChannelFutureListener)
            this::disconnectAndCloseChannel);
    }
  }

  private void disconnectAndCloseChannel(ChannelFuture future) {
    future
        .channel()
        .disconnect()
        .addListener(
            disconnectFuture -> {
              if (disconnectFuture.isSuccess()) {
                future
                    .channel()
                    .close()
                    .addListener(
                        closeFuture -> {
                          if (disconnectFuture.isSuccess()) {
                            if (log.isTraceEnabled()) {
                              log.trace("disconnected and closed socket {}", future.channel().localAddress());
                            }
                          } else {
                            if (log.isWarnEnabled()) {
                              log.warn("exception closing socket {}", future.channel().localAddress(), disconnectFuture.cause());
                            }
                          }
                        });
              } else if (log.isWarnEnabled()) {
                log.warn("exception disconnecting socket {}", future.channel().localAddress(), disconnectFuture.cause());
              }
            });
  }
}
