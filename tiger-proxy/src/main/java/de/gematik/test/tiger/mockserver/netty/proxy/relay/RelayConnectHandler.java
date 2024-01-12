package de.gematik.test.tiger.mockserver.netty.proxy.relay;

import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.connectionClosedException;
import static de.gematik.test.tiger.mockserver.logging.MockServerLogger.isEnabled;
import static de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler.getRemoteAddress;
import static de.gematik.test.tiger.mockserver.model.Protocol.HTTP_2;
import static de.gematik.test.tiger.mockserver.netty.unification.PortUnificationHandler.*;
import static de.gematik.test.tiger.mockserver.socket.tls.SniHandler.getALPNProtocol;
import static org.slf4j.event.Level.TRACE;

import de.gematik.test.tiger.mockserver.configuration.Configuration;
import de.gematik.test.tiger.mockserver.lifecycle.LifeCycle;
import de.gematik.test.tiger.mockserver.log.model.LogEntry;
import de.gematik.test.tiger.mockserver.logging.LoggingHandler;
import de.gematik.test.tiger.mockserver.logging.MockServerLogger;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http2.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.SslHandler;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.slf4j.event.Level;

@Sharable
public abstract class RelayConnectHandler<T> extends SimpleChannelInboundHandler<T> {

  public static final String PROXIED = "PROXIED_";
  public static final String PROXIED_SECURE = PROXIED + "SECURE_";
  public static final String PROXIED_RESPONSE = "PROXIED_RESPONSE_";
  private final Configuration configuration;
  private final LifeCycle server;
  private final MockServerLogger mockServerLogger;
  protected final String host;
  protected final int port;

  public RelayConnectHandler(
      Configuration configuration,
      LifeCycle server,
      MockServerLogger mockServerLogger,
      String host,
      int port) {
    this.configuration = configuration;
    this.server = server;
    this.mockServerLogger = mockServerLogger;
    this.host = host;
    this.port = port;
  }

  @Override
  public void channelRead0(final ChannelHandlerContext proxyClientCtx, final T request) {
    Bootstrap bootstrap =
        new Bootstrap()
            .group(proxyClientCtx.channel().eventLoop())
            .channel(NioSocketChannel.class)
            .handler(
                new ChannelInboundHandlerAdapter() {
                  @Override
                  public void channelActive(final ChannelHandlerContext mockServerCtx) {
                    if (isSslEnabledUpstream(proxyClientCtx.channel())) {
                      mockServerCtx
                          .writeAndFlush(
                              Unpooled.copiedBuffer(
                                  (PROXIED_SECURE + host + ":" + port)
                                      .getBytes(StandardCharsets.UTF_8)))
                          .awaitUninterruptibly();
                    } else {
                      mockServerCtx
                          .writeAndFlush(
                              Unpooled.copiedBuffer(
                                  (PROXIED + host + ":" + port).getBytes(StandardCharsets.UTF_8)))
                          .awaitUninterruptibly();
                    }
                  }

                  @Override
                  public void channelRead(ChannelHandlerContext mockServerCtx, Object msg) {
                    if (msg instanceof ByteBuf) {
                      byte[] bytes = ByteBufUtil.getBytes((ByteBuf) msg);
                      if (new String(bytes, StandardCharsets.UTF_8).startsWith(PROXIED_RESPONSE)) {
                        proxyClientCtx
                            .writeAndFlush(successResponse(request))
                            .addListener(
                                (ChannelFutureListener)
                                    channelFuture -> {
                                      removeCodecSupport(proxyClientCtx);
                                      // TODO(jamesdbloom) this is never true - probably due to race
                                      // condition
                                      boolean http2EnabledDownstream =
                                          HTTP_2.equals(
                                              getALPNProtocol(mockServerLogger, proxyClientCtx));

                                      // upstream (to MockServer)
                                      ChannelPipeline pipelineToMockServer =
                                          mockServerCtx.channel().pipeline();

                                      if (isSslEnabledDownstream(proxyClientCtx.channel())) {
                                        pipelineToMockServer.addLast(
                                            nettySslContextFactory(proxyClientCtx.channel())
                                                .createClientSslContext(
                                                    true, http2EnabledDownstream)
                                                .newHandler(mockServerCtx.alloc(), host, port));
                                      }

                                      if (isEnabled(TRACE)) {
                                        pipelineToMockServer.addLast(
                                            new LoggingHandler(
                                                RelayConnectHandler.class.getName()
                                                    + "-downstream -->"));
                                      }

                                      pipelineToMockServer.addLast(
                                          new HttpClientCodec(
                                              configuration.maxInitialLineLength(),
                                              configuration.maxHeaderSize(),
                                              configuration.maxChunkSize()));
                                      pipelineToMockServer.addLast(new HttpContentDecompressor());
                                      pipelineToMockServer.addLast(
                                          new HttpObjectAggregator(Integer.MAX_VALUE));

                                      pipelineToMockServer.addLast(
                                          new DownstreamProxyRelayHandler(
                                              mockServerLogger, proxyClientCtx.channel()));

                                      // downstream (to proxy client)
                                      ChannelPipeline pipelineToProxyClient =
                                          proxyClientCtx.channel().pipeline();

                                      if (isSslEnabledUpstream(proxyClientCtx.channel())
                                          && pipelineToProxyClient.get(SslHandler.class) == null) {
                                        pipelineToProxyClient.addLast(
                                            nettySslContextFactory(proxyClientCtx.channel())
                                                .createServerSslContext()
                                                .newHandler(proxyClientCtx.alloc()));
                                      }

                                      if (isEnabled(TRACE)) {
                                        pipelineToProxyClient.addLast(
                                            new LoggingHandler(
                                                RelayConnectHandler.class.getName()
                                                    + "-upstream <-- "));
                                      }

                                      if (http2EnabledDownstream) {
                                        final Http2Connection connection =
                                            new DefaultHttp2Connection(true);
                                        final HttpToHttp2ConnectionHandlerBuilder
                                            http2ConnectionHandlerBuilder =
                                                new HttpToHttp2ConnectionHandlerBuilder()
                                                    .frameListener(
                                                        new DelegatingDecompressorFrameListener(
                                                            connection,
                                                            new InboundHttp2ToHttpAdapterBuilder(
                                                                    connection)
                                                                .maxContentLength(Integer.MAX_VALUE)
                                                                .propagateSettings(true)
                                                                .validateHttpHeaders(false)
                                                                .build()));
                                        if (isEnabled(TRACE)) {
                                          http2ConnectionHandlerBuilder.frameLogger(
                                              new Http2FrameLogger(
                                                  LogLevel.TRACE,
                                                  RelayConnectHandler.class.getName()));
                                        }
                                        pipelineToProxyClient.addLast(
                                            http2ConnectionHandlerBuilder
                                                .connection(connection)
                                                .build());
                                      } else {
                                        pipelineToProxyClient.addLast(
                                            new HttpServerCodec(
                                                configuration.maxInitialLineLength(),
                                                configuration.maxHeaderSize(),
                                                configuration.maxChunkSize()));
                                        pipelineToProxyClient.addLast(
                                            new HttpContentDecompressor());
                                        pipelineToProxyClient.addLast(
                                            new HttpObjectAggregator(Integer.MAX_VALUE));
                                      }

                                      pipelineToProxyClient.addLast(
                                          new UpstreamProxyRelayHandler(
                                              mockServerLogger,
                                              proxyClientCtx.channel(),
                                              mockServerCtx.channel()));
                                    });
                      } else {
                        mockServerCtx.fireChannelRead(msg);
                      }
                    }
                  }
                });

    final InetSocketAddress remoteSocket = getDownstreamSocket(proxyClientCtx);
    bootstrap
        .connect(remoteSocket)
        .addListener(
            (ChannelFutureListener)
                future -> {
                  if (!future.isSuccess()) {
                    failure(
                        "Connection failed to " + remoteSocket,
                        future.cause(),
                        proxyClientCtx,
                        failureResponse(request));
                  }
                });
  }

  private InetSocketAddress getDownstreamSocket(ChannelHandlerContext ctx) {
    InetSocketAddress remoteAddress = getRemoteAddress(ctx);
    if (remoteAddress != null) {
      return remoteAddress;
    } else {
      return new InetSocketAddress(server.getLocalPort());
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    failure(
        "Exception caught by CONNECT proxy handler -> closing pipeline ",
        cause,
        ctx,
        failureResponse(null));
  }

  private void failure(
      String message, Throwable cause, ChannelHandlerContext ctx, Object response) {
    if (connectionClosedException(cause)) {
      mockServerLogger.logEvent(
          new LogEntry().setLogLevel(Level.ERROR).setMessageFormat(message).setThrowable(cause));
    }
    Channel channel = ctx.channel();
    channel.writeAndFlush(response);
    if (channel.isActive()) {
      channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }
  }

  protected abstract void removeCodecSupport(ChannelHandlerContext ctx);

  protected abstract Object successResponse(Object request);

  protected abstract Object failureResponse(Object request);

  protected void removeHandler(
      ChannelPipeline pipeline, Class<? extends ChannelHandler> handlerType) {
    if (pipeline.get(handlerType) != null) {
      pipeline.remove(handlerType);
    }
  }

  protected void removeHandler(ChannelPipeline pipeline, ChannelHandler channelHandler) {
    if (pipeline.toMap().containsValue(channelHandler)) {
      pipeline.remove(channelHandler);
    }
  }
}
