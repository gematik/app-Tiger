package de.gematik.test.tiger.mockserver.netty.responsewriter;

import static org.slf4j.event.Level.TRACE;
import static org.slf4j.event.Level.WARN;

import de.gematik.test.tiger.mockserver.configuration.Configuration;
import de.gematik.test.tiger.mockserver.log.model.LogEntry;
import de.gematik.test.tiger.mockserver.logging.MockServerLogger;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.HttpResponse;
import de.gematik.test.tiger.mockserver.responsewriter.ResponseWriter;
import de.gematik.test.tiger.mockserver.scheduler.Scheduler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

public class NettyResponseWriter extends ResponseWriter {

  private final ChannelHandlerContext ctx;
  private final Scheduler scheduler;

  public NettyResponseWriter(
      Configuration configuration,
      MockServerLogger mockServerLogger,
      ChannelHandlerContext ctx,
      Scheduler scheduler) {
    super(configuration, mockServerLogger);
    this.ctx = ctx;
    this.scheduler = scheduler;
  }

  @Override
  public void sendResponse(HttpRequest request, HttpResponse response) {
    writeAndCloseSocket(ctx, request, response);
  }

  private void writeAndCloseSocket(
      final ChannelHandlerContext ctx, final HttpRequest request, HttpResponse response) {
    boolean closeChannel = !(request.isKeepAlive() != null && request.isKeepAlive());

    ChannelFuture channelFuture = ctx.writeAndFlush(response);
    if (closeChannel || configuration.alwaysCloseSocketConnections()) {
      channelFuture.addListener(
          (ChannelFutureListener)
              future -> {
                disconnectAndCloseChannel(future);
              });
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
                            if (MockServerLogger.isEnabled(TRACE)) {
                              mockServerLogger.logEvent(
                                  new LogEntry()
                                      .setLogLevel(TRACE)
                                      .setMessageFormat(
                                          "disconnected and closed socket "
                                              + future.channel().localAddress()));
                            }
                          } else {
                            if (MockServerLogger.isEnabled(WARN)) {
                              mockServerLogger.logEvent(
                                  new LogEntry()
                                      .setLogLevel(WARN)
                                      .setMessageFormat(
                                          "exception closing socket "
                                              + future.channel().localAddress())
                                      .setThrowable(disconnectFuture.cause()));
                            }
                          }
                        });
              } else if (MockServerLogger.isEnabled(WARN)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(WARN)
                        .setMessageFormat(
                            "exception disconnecting socket " + future.channel().localAddress())
                        .setThrowable(disconnectFuture.cause()));
              }
            });
  }
}
