/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.netty.responsewriter;

import static de.gematik.test.tiger.mockserver.model.HttpResponse.notFoundResponse;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import de.gematik.test.tiger.mockserver.configuration.Configuration;
import de.gematik.test.tiger.mockserver.model.Header;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.HttpResponse;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Slf4j
public class NettyResponseWriter {

  private final ChannelHandlerContext ctx;
  private final Configuration configuration;

  public NettyResponseWriter(Configuration configuration, ChannelHandlerContext ctx) {
    this.configuration = configuration;
    this.ctx = ctx;
  }

  public void writeResponse(final HttpRequest request, HttpResponse response) {
    if (response == null) {
      response = notFoundResponse();
    }
    String contentLengthHeader = response.getFirstHeader(CONTENT_LENGTH.toString());
    if (isNotBlank(contentLengthHeader)) {
      try {
        int contentLength = Integer.parseInt(contentLengthHeader);
        if (response.getBodyAsRawBytes().length > contentLength) {
          log.info(
              "returning response with content-length header {} which is smaller then response body"
                  + " length {}, body will likely be truncated by client receiving request",
              contentLength,
              response.getBodyAsRawBytes().length);
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
    HttpResponse responseWithConnectionHeader = response.clone();

    if (Boolean.TRUE.equals(request.getKeepAlive())) {
      responseWithConnectionHeader.replaceHeader(
          new Header(CONNECTION.toString(), KEEP_ALIVE.toString()));
    } else {
      responseWithConnectionHeader.replaceHeader(
          new Header(CONNECTION.toString(), CLOSE.toString()));
    }

    return responseWithConnectionHeader;
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
