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
package de.gematik.test.tiger.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;

/**
 * A TLS server that only advertises http/1.1 in ALPN (no h2). Used for testing that the Tiger Proxy
 * correctly probes backend ALPN capabilities and restricts its own ALPN list accordingly.
 */
public class H1TlsServer implements Closeable {

  @Getter private int port;
  private final EventLoopGroup bossGroup =
      new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
  private final EventLoopGroup workerGroup =
      new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
  private Channel serverChannel;
  @Getter private final AtomicInteger requestsReceived = new AtomicInteger(0);

  public H1TlsServer(int port) {
    this.port = port;
  }

  public void start() throws Exception {
    SslContext sslCtx = buildSslContext();

    ServerBootstrap b = new ServerBootstrap();
    b.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .childHandler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(sslCtx.newHandler(ch.alloc()));
                ch.pipeline().addLast(new HttpServerCodec());
                ch.pipeline().addLast(new HttpObjectAggregator(1024 * 1024));
                ch.pipeline().addLast(new H1ResponseHandler(requestsReceived));
              }
            });
    serverChannel = b.bind(port).sync().channel();
    port = ((java.net.InetSocketAddress) serverChannel.localAddress()).getPort();
  }

  @SuppressWarnings({"deprecation", "java:S1874"})
  private static SslContext buildSslContext() throws Exception {
    SelfSignedCertificate ssc = new SelfSignedCertificate();
    return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
        .protocols("TLSv1.2", "TLSv1.3")
        .applicationProtocolConfig(
            new ApplicationProtocolConfig(
                ApplicationProtocolConfig.Protocol.ALPN,
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                ApplicationProtocolNames.HTTP_1_1))
        .build();
  }

  @Override
  public void close() {
    if (serverChannel != null) {
      serverChannel.close();
    }
    bossGroup.shutdownGracefully();
    workerGroup.shutdownGracefully();
  }

  private static class H1ResponseHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final AtomicInteger requestsReceived;

    H1ResponseHandler(AtomicInteger requestsReceived) {
      this.requestsReceived = requestsReceived;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
      requestsReceived.incrementAndGet();
      byte[] body = "{\"h1\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
      DefaultFullHttpResponse response =
          new DefaultFullHttpResponse(
              HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(body));
      response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
      response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, body.length);
      ctx.writeAndFlush(response);
    }
  }
}
