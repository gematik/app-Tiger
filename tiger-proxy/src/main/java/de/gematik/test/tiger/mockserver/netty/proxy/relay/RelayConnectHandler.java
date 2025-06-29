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
package de.gematik.test.tiger.mockserver.netty.proxy.relay;

import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.connectionClosedException;
import static de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler.getRemoteAddress;
import static de.gematik.test.tiger.mockserver.model.HttpProtocol.HTTP_1_1;
import static de.gematik.test.tiger.mockserver.model.HttpProtocol.HTTP_2;
import static de.gematik.test.tiger.mockserver.netty.unification.PortUnificationHandler.*;
import static de.gematik.test.tiger.mockserver.socket.tls.SniHandler.SERVER_IDENTITY;
import static de.gematik.test.tiger.mockserver.socket.tls.SniHandler.getAlpnProtocol;

import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.netty.MockServer;
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
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;

/*
 * @author jamesdbloom
 */
@Sharable
@Slf4j
public abstract class RelayConnectHandler<T> extends SimpleChannelInboundHandler<T> {

  public static final String PROXIED = "PROXIED_";
  public static final String PROXIED_SECURE = PROXIED + "SECURE_";
  public static final String PROXIED_RESPONSE = "PROXIED_RESPONSE_";
  private final MockServerConfiguration configuration;
  private final MockServer server;
  protected final String host;
  protected final int port;

  public RelayConnectHandler(
      MockServerConfiguration configuration, MockServer server, String host, int port) {
    this.configuration = configuration;
    this.server = server;
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
                                      val httpProtocol =
                                          getAlpnProtocol(proxyClientCtx).orElse(HTTP_1_1);

                                      // upstream (to MockServer)
                                      ChannelPipeline pipelineToMockServer =
                                          mockServerCtx.channel().pipeline();

                                      if (isSslEnabledDownstream(proxyClientCtx.channel())) {
                                        log.info(
                                            "Adding SSL Handler in"
                                                + " RelayConnectHandler.channelRead");
                                        pipelineToMockServer.addLast(
                                            server
                                                .getClientSslContextFactory()
                                                .createClientSslContext(
                                                    httpProtocol,
                                                    ((HttpRequest) request)
                                                        .socketAddressFromHostHeader()
                                                        .getHostName())
                                                .newHandler(mockServerCtx.alloc(), host, port));
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
                                              proxyClientCtx.channel()));

                                      // downstream (to proxy client)
                                      ChannelPipeline pipelineToProxyClient =
                                          proxyClientCtx.channel().pipeline();

                                      if (isSslEnabledUpstream(proxyClientCtx.channel())
                                          && pipelineToProxyClient.get(SslHandler.class) == null) {
                                        final Pair<SslContext, TigerPkiIdentity> serverSslContext =
                                            server
                                                .getServerSslContextFactory()
                                                .createServerSslContext(host);
                                        channelFuture
                                            .channel()
                                            .attr(SERVER_IDENTITY)
                                            .set(serverSslContext.getValue());
                                        pipelineToProxyClient.addLast(
                                            serverSslContext
                                                .getKey()
                                                .newHandler(proxyClientCtx.alloc()));
                                      }

                                      if (httpProtocol == HTTP_2) {
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
                                        if (log.isTraceEnabled()) {
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
                                              server,
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
      log.error(message, cause);
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
