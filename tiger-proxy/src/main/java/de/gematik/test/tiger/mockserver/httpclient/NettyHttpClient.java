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
package de.gematik.test.tiger.mockserver.httpclient;

import static de.gematik.test.tiger.mockserver.httpclient.BinaryBridgeHandler.INCOMING_CHANNEL;
import static de.gematik.test.tiger.mockserver.model.HttpResponse.response;

import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.model.*;
import de.gematik.test.tiger.mockserver.model.BinaryMessage;
import de.gematik.test.tiger.mockserver.model.HttpProtocol;
import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration;
import de.gematik.test.tiger.mockserver.socket.tls.NettySslContextFactory;
import de.gematik.test.tiger.util.NoProxyUtils;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.util.AttributeKey;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Slf4j
public class NettyHttpClient {

  static final AttributeKey<Boolean> SECURE = AttributeKey.valueOf("SECURE");
  public static final AttributeKey<InetSocketAddress> REMOTE_SOCKET =
      AttributeKey.valueOf("REMOTE_SOCKET");
  public static final AttributeKey<NettyHttpClient> HTTP_CLIENT =
      AttributeKey.valueOf("HTTP_CLIENT");
  public static final AttributeKey<CompletableFuture<Message>> RESPONSE_FUTURE =
      AttributeKey.valueOf("RESPONSE_FUTURE");
  public static final AttributeKey<Boolean> ERROR_IF_CHANNEL_CLOSED_WITHOUT_RESPONSE =
      AttributeKey.valueOf("ERROR_IF_CHANNEL_CLOSED_WITHOUT_RESPONSE");
  private final MockServerConfiguration configuration;
  private final EventLoopGroup eventLoopGroup;
  private final ProxyConfiguration proxyConfiguration;
  private final NettySslContextFactory nettySslContextFactory;

  @Getter private final ClientBootstrapFactory clientBootstrapFactory;

  public NettyHttpClient(
      MockServerConfiguration configuration,
      EventLoopGroup eventLoopGroup,
      NettySslContextFactory nettySslContextFactory) {
    this.configuration = configuration;
    this.eventLoopGroup = eventLoopGroup;
    this.proxyConfiguration = configuration.proxyConfiguration();
    this.nettySslContextFactory = nettySslContextFactory;
    this.clientBootstrapFactory = new ClientBootstrapFactory(configuration, eventLoopGroup);
  }

  public CompletableFuture<HttpResponse> sendRequest(
      HttpRequestInfo requestInfo, Long customTimeout) {
    if (requestInfo.getRemoteServerAddress() == null) {
      requestInfo.setRemoteServerAddress(requestInfo.getDataToSend().socketAddressFromHostHeader());
    }
    if (eventLoopGroup.isShuttingDown()) {
      throw new IllegalStateException(
          "Request sent after client has been stopped - the event loop has been shutdown so it is"
              + " not possible to send a request");
    }

    if (shouldUseProxy(requestInfo.getRemoteServerAddress())) {
      modifyProxyInformation(requestInfo);
    }

    if (HttpProtocol.HTTP_2.equals(requestInfo.getDataToSend().getProtocol())
        && Boolean.FALSE.equals(requestInfo.getDataToSend().isSecure())) {
      log.warn(
          "HTTP2 requires ALPN but request is not secure (i.e. TLS) so protocol changed"
              + " to HTTP1");
      requestInfo.getDataToSend().setProtocol(HttpProtocol.HTTP_1_1);
    }

    final CompletableFuture<HttpResponse> httpResponseFuture = new CompletableFuture<>();
    final CompletableFuture<Message> responseFuture = new CompletableFuture<>();
    final HttpProtocol httpProtocol =
        requestInfo.getDataToSend().getProtocol() != null
            ? requestInfo.getDataToSend().getProtocol()
            : HttpProtocol.HTTP_1_1;

    final HttpClientInitializer clientInitializer = createClientInitializer(httpProtocol);

    var isSecure = Optional.ofNullable(requestInfo.getDataToSend().isSecure()).orElse(false);

    var onCreationListener =
        (ChannelFutureListener)
            future -> {
              if (future.isSuccess()) {
                future.channel().attr(INCOMING_CHANNEL).set(requestInfo.getIncomingChannel());
                requestInfo.getIncomingChannel().attr(HTTP_CLIENT).set(this);

                // ensure if HTTP2 is used then settings have been received from server
                clientInitializer.whenComplete(
                    (protocol, throwable) -> {
                      if (throwable != null) {
                        httpResponseFuture.completeExceptionally(throwable);
                      } else {
                        // send the HTTP request
                        log.trace(
                            "sending request: {}",
                            requestInfo.getDataToSend().printLogLineDescription());
                        future.channel().writeAndFlush(requestInfo.getDataToSend());
                      }
                    });
              } else {
                httpResponseFuture.completeExceptionally(future.cause());
              }
            };

    var onReuseListener =
        (ChannelFutureListener)
            future -> {
              if (future.isSuccess()) {
                // send the HTTP request
                log.trace(
                    "sending request: {}", requestInfo.getDataToSend().printLogLineDescription());
                future.channel().writeAndFlush(requestInfo.getDataToSend());
              } else {
                httpResponseFuture.completeExceptionally(future.cause());
              }
            };

    clientBootstrapFactory
        .configureChannel()
        .isSecure(isSecure)
        .requestInfo(requestInfo)
        .clientInitializer(clientInitializer)
        .errorIfChannelClosedWithoutResponse(true)
        .responseFuture(responseFuture)
        .timeoutInMilliseconds(customTimeout)
        .onCreationListener(onCreationListener)
        .onReuseListener(onReuseListener)
        .connectToChannel();

    responseFuture.whenComplete(
        (message, throwable) -> {
          if (throwable == null) {
            if (message != null) {
              httpResponseFuture.complete((HttpResponse) message);
            } else {
              httpResponseFuture.complete(response());
            }
          } else {
            httpResponseFuture.completeExceptionally(throwable);
          }
        });

    return httpResponseFuture;
  }

  private void modifyProxyInformation(HttpRequestInfo requestInfo) {
    if (proxyConfiguration != null
        && Boolean.FALSE.equals(requestInfo.getDataToSend().isSecure())) {
      requestInfo.setRemoteServerAddress(proxyConfiguration.getProxyAddress());
      final String remoteAddress =
          requestInfo.getRemoteServerAddress().getHostString()
              + ":"
              + requestInfo.getRemoteServerAddress().getPort();

      requestInfo.getDataToSend().setSenderAddress(remoteAddress);

      proxyConfiguration.addProxyAuthenticationHeader(requestInfo.getDataToSend());
    } else if (requestInfo.getRemoteServerAddress() == null) {
      log.info(
          "Setting remote server address to {}",
          requestInfo.getDataToSend().socketAddressFromHostHeader());
      requestInfo.setRemoteServerAddress(requestInfo.getDataToSend().socketAddressFromHostHeader());
    }
  }

  public CompletableFuture<HttpResponse> sendRequest(final HttpRequestInfo requestInfo)
      throws SocketConnectionException {
    return sendRequest(requestInfo, configuration.socketConnectionTimeoutInMillis());
  }

  public HttpClientInitializer createClientInitializer(HttpProtocol httpProtocol) {
    return new HttpClientInitializer(configuration, nettySslContextFactory, httpProtocol);
  }

  public CompletableFuture<BinaryMessage> sendRequest(
      BinaryRequestInfo binaryRequestInfo, final boolean isSecure)
      throws SocketConnectionException {
    if (!eventLoopGroup.isShuttingDown()) {
      final CompletableFuture<BinaryMessage> binaryResponseFuture = new CompletableFuture<>();
      final CompletableFuture<Message> responseFuture = new CompletableFuture<>();

      var httpClientInitializer = createClientInitializer(null);

      var onCreateAndReuseListener =
          (ChannelFutureListener)
              future -> {
                if (future.isSuccess()) {
                  log.atDebug()
                      .addArgument(() -> ByteBufUtil.hexDump(binaryRequestInfo.getBytes()))
                      .addArgument(future.channel().attr(REMOTE_SOCKET)::get)
                      .log(() -> "sending bytes hex {} to {}");

                  // send the binary request
                  future
                      .channel()
                      .writeAndFlush(Unpooled.copiedBuffer(binaryRequestInfo.getBytes()));
                } else {
                  binaryResponseFuture.completeExceptionally(future.cause());
                }
              };

      clientBootstrapFactory
          .configureChannel()
          .isSecure(isSecure)
          .requestInfo(binaryRequestInfo)
          .responseFuture(responseFuture)
          .clientInitializer(httpClientInitializer)
          .errorIfChannelClosedWithoutResponse(false)
          .onReuseListener(onCreateAndReuseListener)
          .onCreationListener(onCreateAndReuseListener)
          .connectToChannel();

      responseFuture.whenComplete(
          (message, throwable) -> {
            if (throwable == null) {
              binaryResponseFuture.complete((BinaryMessage) message);
            } else {
              throwable.printStackTrace(); // NOSONAR
              binaryResponseFuture.completeExceptionally(throwable);
            }
          });

      return binaryResponseFuture;
    } else {
      throw new IllegalStateException(
          "Request sent after client has been stopped - the event loop has been shutdown so it is"
              + " not possible to send a request");
    }
  }

  private boolean shouldUseProxy(InetSocketAddress remoteAddress) {
    if (remoteAddress == null || proxyConfiguration == null) {
      return true;
    }
    return Optional.ofNullable(remoteAddress.getAddress())
        .or(
            () ->
                Optional.ofNullable(remoteAddress.getHostName())
                    .map(
                        adr -> {
                          try {
                            return InetAddress.getByName(adr);
                          } catch (UnknownHostException e) {
                            return null;
                          }
                        }))
        .map(adr -> NoProxyUtils.shouldUseProxyForHost(adr, proxyConfiguration.getNoProxyHosts()))
        .orElse(true);
  }

  public int queryClientPort(int port) {
    return clientBootstrapFactory.getLoopCounterForOpenConnectionFromPort(port);
  }

  public SslContext createClientSslContext(Optional<HttpProtocol> httpProtocol) {
    return nettySslContextFactory.createClientSslContext(httpProtocol);
  }
}
