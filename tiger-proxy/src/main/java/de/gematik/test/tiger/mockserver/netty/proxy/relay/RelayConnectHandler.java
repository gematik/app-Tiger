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
import static de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient.REMOTE_SOCKET;
import static de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler.getRemoteAddress;
import static de.gematik.test.tiger.mockserver.netty.HttpRequestHandler.PROXYING;
import static de.gematik.test.tiger.mockserver.netty.unification.PortUnificationHandler.isSslEnabledUpstream;

import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.netty.MockServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Abstract handler for CONNECT requests. Handles the common logic for setting up tunnels, while
 * protocol-specific details (HTTP codecs, responses) are delegated to subclasses.
 *
 * @author jamesdbloom
 */
@Sharable
@Slf4j
public abstract class RelayConnectHandler<T> extends SimpleChannelInboundHandler<T> {

  public static final String PROXIED = "PROXIED_";
  public static final String PROXIED_SECURE = PROXIED + "SECURE_";
  public static final String PROXIED_RESPONSE = "PROXIED_RESPONSE_";

  @Getter protected final MockServerConfiguration configuration;
  @Getter protected final MockServer server;
  protected final String host;
  protected final int port;

  protected RelayConnectHandler(
      MockServerConfiguration configuration, MockServer server, String host, int port) {
    this.configuration = configuration;
    this.server = server;
    this.host = host;
    this.port = port;
  }

  @Override
  public void channelRead0(final ChannelHandlerContext proxyClientCtx, final T request) {
    InetSocketAddress forwardProxy = getRemoteAddress(proxyClientCtx);
    if (forwardProxy == null) {
      setupLocalTunnel(proxyClientCtx, request);
      return;
    }

    connectToForwardProxy(proxyClientCtx, request, forwardProxy);
  }

  private void connectToForwardProxy(
      ChannelHandlerContext proxyClientCtx, T request, InetSocketAddress forwardProxy) {
    InetSocketAddress targetSocket = InetSocketAddress.createUnresolved(host, port);
    proxyClientCtx.channel().attr(REMOTE_SOCKET).set(targetSocket);

    new Bootstrap()
        .group(proxyClientCtx.channel().eventLoop())
        .channel(NioSocketChannel.class)
        .handler(createForwardProxyHandler(proxyClientCtx, request))
        .connect(forwardProxy)
        .addListener(
            (ChannelFutureListener)
                future -> {
                  if (!future.isSuccess()) {
                    failure(
                        "Connection failed to " + forwardProxy,
                        future.cause(),
                        proxyClientCtx,
                        failureResponse(request));
                  }
                });
  }

  /**
   * Creates a handler for communication with the forward proxy using the PROXIED binary protocol.
   */
  protected ChannelInboundHandlerAdapter createForwardProxyHandler(
      ChannelHandlerContext proxyClientCtx, T request) {
    return new ForwardProxyHandler(proxyClientCtx, request);
  }

  /** Handles communication with a forward proxy using the PROXIED binary protocol. */
  private class ForwardProxyHandler extends ChannelInboundHandlerAdapter {
    private final ChannelHandlerContext proxyClientCtx;
    private final T request;

    ForwardProxyHandler(ChannelHandlerContext proxyClientCtx, T request) {
      this.proxyClientCtx = proxyClientCtx;
      this.request = request;
    }

    @Override
    public void channelActive(ChannelHandlerContext forwardProxyCtx) {
      String message =
          (isSslEnabledUpstream(proxyClientCtx.channel()) ? PROXIED_SECURE : PROXIED)
              + host
              + ":"
              + port;

      forwardProxyCtx
          .writeAndFlush(Unpooled.copiedBuffer(message.getBytes(StandardCharsets.UTF_8)))
          .awaitUninterruptibly();
    }

    @Override
    public void channelRead(ChannelHandlerContext forwardProxyCtx, Object msg) {
      if (!(msg instanceof ByteBuf byteBuf)) {
        forwardProxyCtx.fireChannelRead(msg);
        return;
      }

      String response = new String(ByteBufUtil.getBytes(byteBuf), StandardCharsets.UTF_8);
      if (!response.startsWith(PROXIED_RESPONSE)) {
        forwardProxyCtx.fireChannelRead(msg);
        return;
      }

      proxyClientCtx
          .writeAndFlush(successResponse(request))
          .addListener(
              (ChannelFutureListener)
                  future -> {
                    if (future.isSuccess()) {
                      configureRelayPipelines(proxyClientCtx, forwardProxyCtx, request, future);
                    }
                  });
    }
  }

  /**
   * Handles the case where no forward proxy is configured. Sets up local routing and sends success
   * response to the client, then prepares the pipeline for subsequent traffic.
   */
  private void setupLocalTunnel(ChannelHandlerContext proxyClientCtx, T request) {
    InetSocketAddress targetSocket = InetSocketAddress.createUnresolved(host, port);
    proxyClientCtx.channel().attr(REMOTE_SOCKET).set(targetSocket);
    proxyClientCtx.channel().attr(PROXYING).set(Boolean.TRUE);

    log.trace("Setting up local tunnel for {}:{}", host, port);

    proxyClientCtx
        .writeAndFlush(successResponse(request))
        .addListener(
            (ChannelFutureListener)
                channelFuture -> {
                  if (channelFuture.isSuccess()) {
                    prepareForSubsequentTraffic(proxyClientCtx);
                  } else {
                    log.error("Failed to send success response to client", channelFuture.cause());
                  }
                });
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    failure(
        "Exception caught by CONNECT proxy handler -> closing pipeline ",
        cause,
        ctx,
        failureResponse(null));
  }

  protected void failure(
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

  // ========== Abstract methods to be implemented by protocol-specific subclasses ==========

  /** Removes protocol-specific codecs from the pipeline. */
  protected abstract void removeCodecSupport(ChannelHandlerContext ctx);

  /** Creates a success response appropriate for the protocol. */
  protected abstract Object successResponse(Object request);

  /** Creates a failure response appropriate for the protocol. */
  protected abstract Object failureResponse(Object request);

  /** Configures relay pipelines after successful connection to forward proxy. */
  protected abstract void configureRelayPipelines(
      ChannelHandlerContext proxyClientCtx,
      ChannelHandlerContext forwardProxyCtx,
      T request,
      ChannelFuture clientFuture);

  /** Prepares the pipeline for subsequent traffic after local tunnel setup. */
  protected abstract void prepareForSubsequentTraffic(ChannelHandlerContext ctx);

  // ========== Utility methods for subclasses ==========

  protected void removeHandler(
      ChannelPipeline pipeline, Class<? extends ChannelHandler> handlerType) {
    if (pipeline.get(handlerType) != null) {
      pipeline.remove(handlerType);
    }
  }
}
