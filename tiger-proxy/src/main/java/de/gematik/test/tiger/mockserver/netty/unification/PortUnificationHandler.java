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

package de.gematik.test.tiger.mockserver.netty.unification;

import static de.gematik.test.tiger.mockserver.character.Character.NEW_LINE;
import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.*;
import static de.gematik.test.tiger.mockserver.httpclient.BinaryBridgeHandler.INCOMING_CHANNEL;
import static de.gematik.test.tiger.mockserver.httpclient.BinaryBridgeHandler.OUTGOING_CHANNEL;
import static de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler.REMOTE_SOCKET;
import static de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler.getRemoteAddress;
import static de.gematik.test.tiger.mockserver.netty.HttpRequestHandler.LOCAL_HOST_HEADERS;
import static de.gematik.test.tiger.mockserver.netty.HttpRequestHandler.PROXYING;
import static de.gematik.test.tiger.mockserver.netty.proxy.relay.RelayConnectHandler.*;
import static java.util.Collections.unmodifiableSet;

import de.gematik.test.tiger.mockserver.codec.MockServerHttpServerCodec;
import de.gematik.test.tiger.mockserver.configuration.Configuration;
import de.gematik.test.tiger.mockserver.mappers.MockServerHttpResponseToFullHttpResponse;
import de.gematik.test.tiger.mockserver.mock.HttpState;
import de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler;
import de.gematik.test.tiger.mockserver.netty.HttpRequestHandler;
import de.gematik.test.tiger.mockserver.netty.MockServer;
import de.gematik.test.tiger.mockserver.netty.proxy.BinaryHandler;
import de.gematik.test.tiger.mockserver.socket.tls.NettySslContextFactory;
import de.gematik.test.tiger.mockserver.socket.tls.SniHandler;
import de.gematik.test.tiger.proxy.data.TigerConnectionStatus;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;

/*
 * @author jamesdbloom
 */
@Slf4j
public class PortUnificationHandler extends ReplayingDecoder<Void> {

  public static final AttributeKey<Boolean> TLS_ENABLED_UPSTREAM =
      AttributeKey.valueOf("TLS_ENABLED_UPSTREAM");
  public static final AttributeKey<Boolean> TLS_ENABLED_DOWNSTREAM =
      AttributeKey.valueOf("TLS_ENABLED_DOWNSTREAM");
  public static final AttributeKey<NettySslContextFactory> NETTY_SSL_CONTEXT_FACTORY =
      AttributeKey.valueOf("NETTY_SSL_CONTEXT_FACTORY");
  private static final Map<PortBinding, Set<String>> localAddressesCache =
      new ConcurrentHashMap<>();
  private final HttpContentLengthRemover httpContentLengthRemover = new HttpContentLengthRemover();
  private final Configuration configuration;
  private final MockServer server;
  private final HttpState httpState;
  private final HttpActionHandler actionHandler;
  private final NettySslContextFactory nettySslContextFactory;
  private final MockServerHttpResponseToFullHttpResponse mockServerHttpResponseToFullHttpResponse;

  public PortUnificationHandler(
      Configuration configuration,
      MockServer server,
      HttpState httpState,
      HttpActionHandler actionHandler,
      NettySslContextFactory nettySslContextFactory) {
    this.configuration = configuration;
    this.server = server;
    this.httpState = httpState;
    this.actionHandler = actionHandler;
    this.nettySslContextFactory = nettySslContextFactory;
    this.mockServerHttpResponseToFullHttpResponse = new MockServerHttpResponseToFullHttpResponse();
  }

  private void performConnectionToRemote(ChannelHandlerContext ctx) {
    var incomingChannel = ctx.channel();
    if (incomingChannel.attr(OUTGOING_CHANNEL).get() != null
        || configuration.binaryProxyListener() == null) {
      log.trace("already connected to remote server or no binary proxy listener");
      return;
    }
    log.trace("enabling connection to remote server");
    var remoteAddress = getRemoteAddress(ctx);

    val httpClient = actionHandler.getHttpClient();

    var channelFuture =
        httpClient
            .getClientBootstrapFactory()
            .configureChannel()
            .isSecure(isSslEnabledUpstream(incomingChannel))
            .incomingChannel(incomingChannel)
            .remoteAddress(remoteAddress)
            .clientInitializer(httpClient.createClientInitializer(null))
            .eventLoopGroup(incomingChannel.eventLoop())
            .errorIfChannelClosedWithoutResponse(false)
            .connectToChannel();
    channelFuture.addListener(
        (ChannelFutureListener)
            future -> {
              // we set these attributes also when the channel did not open succesfully, so that
              // we don't try again to open the outgoing channel
              incomingChannel.attr(OUTGOING_CHANNEL).set(future.channel());
              future.channel().attr(INCOMING_CHANNEL).set(incomingChannel);
              if (!future.isSuccess()) {
                log.error("Failed to connect to {}", remoteAddress, future.cause());
              }
            });
  }

  public static NettySslContextFactory nettySslContextFactory(Channel channel) {
    if (channel.attr(NETTY_SSL_CONTEXT_FACTORY).get() != null) {
      return channel.attr(NETTY_SSL_CONTEXT_FACTORY).get();
    } else {
      throw new RuntimeException(
          "NettySslContextFactory not yet initialised for channel " + channel);
    }
  }

  public static void enableSslUpstreamAndDownstream(Channel channel) {
    channel.attr(TLS_ENABLED_UPSTREAM).set(Boolean.TRUE);
    channel.attr(TLS_ENABLED_DOWNSTREAM).set(Boolean.TRUE);
  }

  public static boolean isSslEnabledUpstream(Channel channel) {
    if (channel.attr(TLS_ENABLED_UPSTREAM).get() != null) {
      return channel.attr(TLS_ENABLED_UPSTREAM).get();
    } else {
      return false;
    }
  }

  public static boolean isSslEnabledDownstream(Channel channel) {
    if (channel.attr(TLS_ENABLED_DOWNSTREAM).get() != null) {
      return channel.attr(TLS_ENABLED_DOWNSTREAM).get();
    } else {
      return false;
    }
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
    ctx.channel().attr(NETTY_SSL_CONTEXT_FACTORY).set(nettySslContextFactory);
    if (isTls(msg)) {
      logStage(ctx, "adding TLS decoders");
      enableTls(ctx, msg);
      performConnectionToRemote(ctx);
    } else {
      performConnectionToRemote(ctx);
      if (configuration.binaryProxyListener() == null) {
        if (isHttp(msg)) {
          logStage(ctx, "adding HTTP decoders");
          switchToHttp(ctx, msg);
        } else if (isProxyConnected(msg)) {
          logStage(ctx, "setting proxy connected");
          switchToProxyConnected(ctx, msg);
        }
      } else {
        logStage(ctx, "adding binary decoder");
        switchToBinary(ctx, msg);
      }
    }
  }

  private void logStage(ChannelHandlerContext ctx, String message) {
    if (log.isTraceEnabled()) {
      log.trace(
          message + " for channel:{}pipeline:{}", ctx.channel().toString(), ctx.pipeline().names());
    }
  }

  private boolean isTls(ByteBuf buf) {
    return SslHandler.isEncrypted(buf);
  }

  private void enableTls(ChannelHandlerContext ctx, ByteBuf msg) {
    ChannelPipeline pipeline = ctx.pipeline();
    server.addConnectionWithStatus(ctx.channel().remoteAddress(), TigerConnectionStatus.OPEN_TLS);
    pipeline.addFirst(new SniHandler(configuration, nettySslContextFactory));
    enableSslUpstreamAndDownstream(ctx.channel());

    // re-unify (with SSL enabled)
    ctx.pipeline().fireChannelRead(msg.readBytes(actualReadableBytes()));
  }

  private boolean isHttp(ByteBuf msg) {
    if (msg.writerIndex() < 8) {
      return false;
    }

    String method = msg.toString(msg.readerIndex(), 8, StandardCharsets.US_ASCII);
    return method.startsWith("GET ")
        || method.startsWith("POST ")
        || method.startsWith("PUT ")
        || method.startsWith("HEAD ")
        || method.startsWith("OPTIONS ")
        || method.startsWith("PATCH ")
        || method.startsWith("DELETE ")
        || method.startsWith("TRACE ")
        || method.startsWith("CONNECT ");
  }

  private void switchToHttp(ChannelHandlerContext ctx, ByteBuf msg) {
    ChannelPipeline pipeline = ctx.pipeline();

    addLastIfNotPresent(
        pipeline,
        new HttpServerCodec(
            configuration.maxInitialLineLength(),
            configuration.maxHeaderSize(),
            configuration.maxChunkSize()));
    addLastIfNotPresent(pipeline, new HttpContentDecompressor());
    addLastIfNotPresent(pipeline, httpContentLengthRemover);
    addLastIfNotPresent(pipeline, new HttpObjectAggregator(Integer.MAX_VALUE));
    addLastIfNotPresent(
        pipeline,
        new MockServerHttpServerCodec(
            configuration,
            isSslEnabledUpstream(ctx.channel()),
            SniHandler.retrieveClientCertificates(ctx),
            ctx.channel().localAddress()));
    addLastIfNotPresent(
        pipeline, new HttpRequestHandler(configuration, server, httpState, actionHandler));
    pipeline.remove(this);

    ctx.channel().attr(LOCAL_HOST_HEADERS).set(getLocalAddresses(ctx));

    // fire message back through pipeline
    ctx.fireChannelRead(msg.readBytes(actualReadableBytes()));
  }

  private boolean isProxyConnected(ByteBuf msg) {
    if (msg.writerIndex() < 8) {
      return false;
    }
    return msg.toString(msg.readerIndex(), 8, StandardCharsets.US_ASCII).startsWith(PROXIED);
  }

  private void switchToProxyConnected(ChannelHandlerContext ctx, ByteBuf msg) {
    String message = readMessage(msg);
    if (message.startsWith(PROXIED_SECURE)) {
      String[] hostParts = StringUtils.substringAfter(message, PROXIED_SECURE).split(":");
      int port = hostParts.length > 1 ? Integer.parseInt(hostParts[1]) : 443;
      enableSslUpstreamAndDownstream(ctx.channel());
      ctx.channel().attr(PROXYING).set(Boolean.TRUE);
      ctx.channel().attr(REMOTE_SOCKET).set(new InetSocketAddress(hostParts[0], port));
    } else if (message.startsWith(PROXIED)) {
      String[] hostParts = StringUtils.substringAfter(message, PROXIED).split(":");
      int port = hostParts.length > 1 ? Integer.parseInt(hostParts[1]) : 80;
      ctx.channel().attr(PROXYING).set(Boolean.TRUE);
      ctx.channel().attr(REMOTE_SOCKET).set(new InetSocketAddress(hostParts[0], port));
    }
    ctx.writeAndFlush(
            Unpooled.copiedBuffer((PROXIED_RESPONSE + message).getBytes(StandardCharsets.UTF_8)))
        .awaitUninterruptibly();
  }

  private String readMessage(ByteBuf msg) {
    byte[] bytes = new byte[actualReadableBytes()];
    msg.readBytes(bytes);
    return new String(bytes, StandardCharsets.US_ASCII);
  }

  private void switchToBinary(ChannelHandlerContext ctx, ByteBuf msg) {
    addLastIfNotPresent(
        ctx.pipeline(), new BinaryHandler(configuration, actionHandler.getHttpClient()));
    // fire message back through pipeline
    ctx.fireChannelRead(msg.readBytes(actualReadableBytes()));
  }

  private Set<String> getLocalAddresses(ChannelHandlerContext ctx) {
    SocketAddress localAddress = ctx.channel().localAddress();
    Set<String> localAddresses = null;
    if (localAddress instanceof InetSocketAddress inetSocketAddress) {
      String portExtension =
          calculatePortExtension(inetSocketAddress, isSslEnabledUpstream(ctx.channel()));
      PortBinding cacheKey = new PortBinding(inetSocketAddress, portExtension);
      localAddresses = localAddressesCache.get(cacheKey);
      if (localAddresses == null) {
        localAddresses = calculateLocalAddresses(inetSocketAddress, portExtension);
        localAddressesCache.put(cacheKey, localAddresses);
      }
    }
    return (localAddresses == null) ? Collections.emptySet() : localAddresses;
  }

  private String calculatePortExtension(
      InetSocketAddress inetSocketAddress, boolean sslEnabledUpstream) {
    String portExtension;
    if (((inetSocketAddress.getPort() == 443) && sslEnabledUpstream)
        || ((inetSocketAddress.getPort() == 80) && !sslEnabledUpstream)) {
      portExtension = "";
    } else {
      portExtension = ":" + inetSocketAddress.getPort();
    }
    return portExtension;
  }

  private Set<String> calculateLocalAddresses(
      InetSocketAddress localAddress, String portExtension) {
    InetAddress socketAddress = localAddress.getAddress();
    Set<String> localAddresses = new HashSet<>();
    localAddresses.add(socketAddress.getHostAddress() + portExtension);
    localAddresses.add(socketAddress.getCanonicalHostName() + portExtension);
    localAddresses.add(socketAddress.getHostName() + portExtension);
    localAddresses.add("localhost" + portExtension);
    localAddresses.add("127.0.0.1" + portExtension);
    return unmodifiableSet(localAddresses);
  }

  private void addLastIfNotPresent(ChannelPipeline pipeline, ChannelHandler channelHandler) {
    if (pipeline.get(channelHandler.getClass()) == null) {
      pipeline.addLast(channelHandler);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) {
    if (connectionClosedException(throwable)) {
      log.error(
          "exception caught by port unification handler -> closing pipeline {}",
          ctx.channel(),
          throwable);
    } else if (sslHandshakeException(throwable)) {
      if (throwable.getMessage().contains("certificate_unknown")) {
        log.warn(
            "TLS handshake failure:"
                + NEW_LINE
                + NEW_LINE
                + " Client does not trust MockServer Certificate Authority for:{}See"
                + " http://mock-server.com/mock_server/HTTPS_TLS.html to enable the"
                + " client to trust MocksServer Certificate Authority."
                + NEW_LINE,
            ctx.channel());
      } else if (!throwable.getMessage().contains("close_notify during handshake")) {
        log.error(
            "TLS handshake failure while a client attempted to connect to {}",
            ctx.channel(),
            throwable);
      }
    }
    closeOnFlush(ctx.channel());
  }
}
