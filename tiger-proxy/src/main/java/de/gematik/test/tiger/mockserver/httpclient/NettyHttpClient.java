/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.mockserver.httpclient;

import static de.gematik.test.tiger.mockserver.model.HttpResponse.response;

import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.model.*;
import de.gematik.test.tiger.mockserver.model.BinaryMessage;
import de.gematik.test.tiger.mockserver.model.Protocol;
import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration;
import de.gematik.test.tiger.mockserver.socket.tls.NettySslContextFactory;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.util.AttributeKey;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/*
 * @author jamesdbloom
 */
@Slf4j
public class NettyHttpClient {

  static final AttributeKey<Boolean> SECURE = AttributeKey.valueOf("SECURE");
  static final AttributeKey<InetSocketAddress> REMOTE_SOCKET =
      AttributeKey.valueOf("REMOTE_SOCKET");
  public static final AttributeKey<CompletableFuture<Message>> RESPONSE_FUTURE =
      AttributeKey.valueOf("RESPONSE_FUTURE");
  public static final AttributeKey<Boolean> ERROR_IF_CHANNEL_CLOSED_WITHOUT_RESPONSE =
      AttributeKey.valueOf("ERROR_IF_CHANNEL_CLOSED_WITHOUT_RESPONSE");
  private final MockServerConfiguration configuration;
  private final EventLoopGroup eventLoopGroup;
  private final Map<ProxyConfiguration.Type, ProxyConfiguration> proxyConfigurations;
  private final NettySslContextFactory nettySslContextFactory;

  @Getter private final ClientBootstrapFactory clientBootstrapFactory;

  public NettyHttpClient(
      MockServerConfiguration configuration,
      EventLoopGroup eventLoopGroup,
      List<ProxyConfiguration> proxyConfigurations,
      NettySslContextFactory nettySslContextFactory) {
    this.configuration = configuration;
    this.eventLoopGroup = eventLoopGroup;
    this.proxyConfigurations =
        proxyConfigurations != null
            ? proxyConfigurations.stream()
                .collect(
                    Collectors.toMap(
                        ProxyConfiguration::getType, proxyConfiguration -> proxyConfiguration))
            : Map.of();
    this.nettySslContextFactory = nettySslContextFactory;
    this.clientBootstrapFactory = new ClientBootstrapFactory(configuration, eventLoopGroup);
  }

  public CompletableFuture<HttpResponse> sendRequest(
      HttpRequestInfo requestInfo, Long customTimeout) {
    if (requestInfo.getRemoteServerAddress() == null) {
      requestInfo.setRemoteServerAddress(requestInfo.getDataToSend().socketAddressFromHostHeader());
    }
    if (!eventLoopGroup.isShuttingDown()) {
      if (proxyConfigurations != null
          && !Boolean.TRUE.equals(requestInfo.getDataToSend().isSecure())
          && proxyConfigurations.containsKey(ProxyConfiguration.Type.HTTP)
          && isHostNotOnNoProxyHostList(requestInfo.getRemoteServerAddress())) {
        ProxyConfiguration proxyConfiguration =
            proxyConfigurations.get(ProxyConfiguration.Type.HTTP);
        requestInfo.setRemoteServerAddress(proxyConfiguration.getProxyAddress());
        proxyConfiguration.addProxyAuthenticationHeader(requestInfo.getDataToSend());
      } else if (requestInfo.getRemoteServerAddress() == null) {
        requestInfo.setRemoteServerAddress(
            requestInfo.getDataToSend().socketAddressFromHostHeader());
      }
      if (Protocol.HTTP_2.equals(requestInfo.getDataToSend().getProtocol())
          && !Boolean.TRUE.equals(requestInfo.getDataToSend().isSecure())) {
        log.warn(
            "HTTP2 requires ALPN but request is not secure (i.e. TLS) so protocol changed"
                + " to HTTP1");
        requestInfo.getDataToSend().setProtocol(Protocol.HTTP_1_1);
      }

      final CompletableFuture<HttpResponse> httpResponseFuture = new CompletableFuture<>();
      final CompletableFuture<Message> responseFuture = new CompletableFuture<>();
      final Protocol httpProtocol =
          requestInfo.getDataToSend().getProtocol() != null
              ? requestInfo.getDataToSend().getProtocol()
              : Protocol.HTTP_1_1;

      final HttpClientInitializer clientInitializer = createClientInitializer(httpProtocol);

      var isSecure =
          requestInfo.getDataToSend().isSecure() != null && requestInfo.getDataToSend().isSecure();

      var onCreationListener =
          (ChannelFutureListener)
              future -> {
                if (future.isSuccess()) {
                  // ensure if HTTP2 is used then settings have been received from server

                  clientInitializer.whenComplete(
                      (protocol, throwable) -> {
                        if (throwable != null) {
                          httpResponseFuture.completeExceptionally(throwable);
                        } else {
                          // send the HTTP request
                          log.trace("sending request: {}", requestInfo.getDataToSend());
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
                  log.trace("sending request: {}", requestInfo.getDataToSend());
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
    } else {
      throw new IllegalStateException(
          "Request sent after client has been stopped - the event loop has been shutdown so it is"
              + " not possible to send a request");
    }
  }

  public CompletableFuture<HttpResponse> sendRequest(final HttpRequestInfo requestInfo)
      throws SocketConnectionException {
    return sendRequest(requestInfo, configuration.socketConnectionTimeoutInMillis());
  }

  public HttpClientInitializer createClientInitializer(Protocol httpProtocol) {
    return new HttpClientInitializer(
        configuration, proxyConfigurations, nettySslContextFactory, httpProtocol);
  }

  public CompletableFuture<BinaryMessage> sendRequest(
      BinaryRequestInfo binaryRequestInfo, final boolean isSecure)
      throws SocketConnectionException {
    if (!eventLoopGroup.isShuttingDown()) {
      if (proxyConfigurations != null
          && !isSecure
          && proxyConfigurations.containsKey(ProxyConfiguration.Type.HTTP)) {
        binaryRequestInfo.setRemoteServerAddress(
            proxyConfigurations.get(ProxyConfiguration.Type.HTTP).getProxyAddress());
      } else if (binaryRequestInfo.getRemoteServerAddress() == null) {
        throw new IllegalArgumentException("Remote address cannot be null");
      }

      final CompletableFuture<BinaryMessage> binaryResponseFuture = new CompletableFuture<>();
      final CompletableFuture<Message> responseFuture = new CompletableFuture<>();

      var httpClientInitializer = createClientInitializer(null);

      var onCreateAndReuseListener =
          (ChannelFutureListener)
              future -> {
                if (future.isSuccess()) {
                  log.atDebug().log(
                      () ->
                          "sending bytes hex %s to %s"
                              .formatted(
                                  ByteBufUtil.hexDump(binaryRequestInfo.getBytes()),
                                  future.channel().attr(REMOTE_SOCKET).get()));

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
          .responseFuture(responseFuture)
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

  private boolean isHostNotOnNoProxyHostList(InetSocketAddress remoteAddress) {
    if (remoteAddress == null || StringUtils.isBlank(configuration.noProxyHosts())) {
      return true;
    }
    return Stream.of(configuration.noProxyHosts().split(","))
        .map(String::trim)
        .map(
            host -> {
              try {
                return InetAddress.getByName(host);
              } catch (UnknownHostException e) {
                return null;
              }
            })
        .filter(Objects::nonNull)
        .noneMatch(remoteAddress.getAddress()::equals);
  }
}
