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
package de.gematik.test.tiger.mockserver.netty.responsewriter;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.model.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Slf4j
public class NettyResponseWriter {

  @Getter private final ChannelHandlerContext ctx;
  private final MockServerConfiguration configuration;

  public NettyResponseWriter(MockServerConfiguration configuration, ChannelHandlerContext ctx) {
    this.configuration = configuration;
    this.ctx = ctx;
  }

  public void writeResponse(final HttpRequest request, Action response) {
    if (response == null) {
      response = new CloseChannel();
    }
    response.write(this, request);
  }

  public void writeHttpResponse(final HttpRequest request, HttpResponse response) {
    String contentLengthHeader = response.getFirstHeader(CONTENT_LENGTH.toString());
    if (isNotBlank(contentLengthHeader)) {
      try {
        int contentLength = Integer.parseInt(contentLengthHeader);
        if (response.getBody().length > contentLength) {
          log.info(
              "returning response with content-length header {} which is smaller then response body"
                  + " length {}, body will likely be truncated by client receiving request",
              contentLength,
              response.getBody().length);
        }
      } catch (NumberFormatException ignore) {
        log.trace("NumberFormatException while parsing content-length header", ignore);
        // ignore exception while parsing invalid content-length header
      }
    }

    // send response down the request HTTP2 stream
    if (request.getStreamId() != null) {
      response.withStreamId(request.getStreamId());
    }

    sendResponse(request, addConnectionHeader(request, response));
  }

  protected HttpResponse addConnectionHeader(
      final HttpRequest request, final HttpResponse response) {
    if (Boolean.TRUE.equals(request.getKeepAlive())) {
      response.replaceHeader(new Header(CONNECTION.toString(), KEEP_ALIVE.toString()));
    } else {
      response.replaceHeader(new Header(CONNECTION.toString(), CLOSE.toString()));
    }

    return response;
  }

  public void sendResponse(HttpRequest request, HttpResponse response) {
    writeAndCloseSocket(ctx, request, response);
  }

  private void writeAndCloseSocket(
      final ChannelHandlerContext ctx, final HttpRequest request, HttpResponse response) {
    boolean closeChannel = !(request.getKeepAlive() != null && request.getKeepAlive());

    ChannelFuture channelFuture = ctx.writeAndFlush(response);
    if (closeChannel || configuration.alwaysCloseSocketConnections()) {
      channelFuture.addListener((ChannelFutureListener) this::disconnectAndCloseChannel);
    }
  }

  public void disconnectAndCloseChannel(ChannelFuture future) {
    gracefulClose(future.channel());
  }

  public void closeChannel() {
    gracefulClose(ctx.channel());
  }

  private static ChannelFuture gracefulClose(Channel channel) {
    return channel
        .disconnect()
        .addListener(
            disconnectFuture -> {
              if (disconnectFuture.isSuccess()) {
                channel
                    .close()
                    .addListener(
                        closeFuture -> {
                          if (disconnectFuture.isSuccess()) {
                            if (log.isTraceEnabled()) {
                              log.trace(
                                  "disconnected and closed socket {}", channel.localAddress());
                            }
                          } else {
                            if (log.isWarnEnabled()) {
                              log.warn(
                                  "exception closing socket {}",
                                  channel.localAddress(),
                                  disconnectFuture.cause());
                            }
                          }
                        });
              } else if (log.isWarnEnabled()) {
                log.warn(
                    "exception disconnecting socket {}",
                    channel.localAddress(),
                    disconnectFuture.cause());
              }
            });
  }
}
