package de.gematik.test.tiger.mockserver.netty.proxy.relay;

import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.closeOnFlush;
import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.connectionClosedException;

import de.gematik.test.tiger.mockserver.log.model.LogEntry;
import de.gematik.test.tiger.mockserver.logging.MockServerLogger;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import org.slf4j.event.Level;

public class DownstreamProxyRelayHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

  private final MockServerLogger mockServerLogger;
  private final Channel upstreamChannel;

  public DownstreamProxyRelayHandler(MockServerLogger mockServerLogger, Channel upstreamChannel) {
    super(false);
    this.upstreamChannel = upstreamChannel;
    this.mockServerLogger = mockServerLogger;
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
                      mockServerLogger.logEvent(
                          new LogEntry()
                              .setLogLevel(Level.ERROR)
                              .setMessageFormat("exception while returning writing " + response)
                              .setThrowable(future.cause()));
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
      mockServerLogger.logEvent(
          new LogEntry()
              .setLogLevel(Level.ERROR)
              .setMessageFormat(
                  "exception caught by downstream relay handler -> closing pipeline "
                      + ctx.channel())
              .setThrowable(cause));
    }
    closeOnFlush(ctx.channel());
  }
}
