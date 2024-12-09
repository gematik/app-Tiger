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

import static de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient.REMOTE_SOCKET;
import static de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient.SECURE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import de.gematik.test.tiger.mockserver.codec.MockServerBinaryClientCodec;
import de.gematik.test.tiger.mockserver.codec.MockServerHttpClientCodec;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.model.HttpProtocol;
import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration;
import de.gematik.test.tiger.mockserver.socket.tls.NettySslContextFactory;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http2.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
@Slf4j
public class HttpClientInitializer extends ChannelInitializer<SocketChannel> {

  private final HttpProtocol httpProtocol;
  private final HttpClientConnectionErrorHandler httpClientConnectionHandler;
  private final CompletableFuture<HttpProtocol> protocolFuture;
  private final HttpClientHandler httpClientHandler;
  private final ProxyConfiguration proxyConfiguration;
  private final NettySslContextFactory nettySslContextFactory;
  private final BinaryBridgeHandler binaryBridgeHandler;

  HttpClientInitializer(
      MockServerConfiguration configuration,
      NettySslContextFactory nettySslContextFactory,
      HttpProtocol httpProtocol) {
    this.proxyConfiguration = configuration.proxyConfiguration();
    this.httpProtocol = httpProtocol;
    this.protocolFuture = new CompletableFuture<>();
    this.httpClientHandler = new HttpClientHandler();
    this.httpClientConnectionHandler = new HttpClientConnectionErrorHandler();
    this.binaryBridgeHandler = new BinaryBridgeHandler(configuration);
    this.nettySslContextFactory = nettySslContextFactory;
  }

  public void whenComplete(BiConsumer<? super HttpProtocol, ? super Throwable> action) {
    protocolFuture.whenComplete(action);
  }

  @Override
  public void initChannel(SocketChannel channel) {
    ChannelPipeline pipeline = channel.pipeline();
    boolean secure =
        channel.attr(SECURE) != null
            && channel.attr(SECURE).get() != null
            && channel.attr(SECURE).get();
    InetSocketAddress remoteAddress = channel.attr(REMOTE_SOCKET).get();

    pipeline.addFirst(new LoggingHandler(LogLevel.DEBUG));
    if (isHostNotOnNoProxyHostList(remoteAddress)) {
      addProxyHandlerIfApplicable(pipeline, secure);
    }
    pipeline.addLast(httpClientConnectionHandler);

    if (secure) {
      log.info("Adding SSL Handler in HttpClientInitializer.initChannel");
      pipeline.addLast(
          nettySslContextFactory
              .createClientSslContext(Optional.ofNullable(httpProtocol))
              .newHandler(channel.alloc(), remoteAddress.getHostName(), remoteAddress.getPort()));
    }

    if (httpProtocol == null) {
      configureBinaryPipeline(pipeline);
    } else {
      if (secure) {
        // use ALPN to determine http1 or http2
        pipeline.addLast(
            new HttpOrHttp2Initializer(this::configureHttp1Pipeline, this::configureHttp2Pipeline));
      } else {
        // default to http1 without TLS
        configureHttp1Pipeline(pipeline);
      }
    }
  }

  private void addProxyHandlerIfApplicable(ChannelPipeline pipeline, boolean secure) {
    if (proxyConfiguration != null) {
      if (proxyConfiguration.getType() == ProxyConfiguration.Type.HTTP && secure) {
        if (isNotBlank(proxyConfiguration.getUsername())
            && isNotBlank(proxyConfiguration.getPassword())) {
          log.atTrace()
              .addArgument(proxyConfiguration.getProxyAddress())
              .addArgument(proxyConfiguration.getUsername())
              .addArgument(proxyConfiguration.getPassword())
              .log("Adding HTTP Proxy Handler with authentication {} with {}:{}");
          pipeline.addLast(
              new HttpProxyHandler(
                  proxyConfiguration.getProxyAddress(),
                  proxyConfiguration.getUsername(),
                  proxyConfiguration.getPassword()));
        } else {
          log.atTrace()
              .addArgument(proxyConfiguration.getProxyAddress())
              .log("Adding HTTP Proxy Handler without authentication {}");
          pipeline.addLast(new HttpProxyHandler(proxyConfiguration.getProxyAddress()));
        }
      } else if (proxyConfiguration.getType() == ProxyConfiguration.Type.SOCKS5) {
        if (isNotBlank(proxyConfiguration.getUsername())
            && isNotBlank(proxyConfiguration.getPassword())) {
          log.atTrace()
              .addArgument(proxyConfiguration.getProxyAddress())
              .addArgument(proxyConfiguration.getUsername())
              .addArgument(proxyConfiguration.getPassword())
              .log("Adding SOCKS5 Proxy Handler with authentication {} with {}:{}");
          pipeline.addLast(
              new Socks5ProxyHandler(
                  proxyConfiguration.getProxyAddress(),
                  proxyConfiguration.getUsername(),
                  proxyConfiguration.getPassword()));
        } else {
          log.atTrace()
              .addArgument(proxyConfiguration.getProxyAddress())
              .log("Adding SOCKS Proxy Handler without authentication {}");
          pipeline.addLast(new Socks5ProxyHandler(proxyConfiguration.getProxyAddress()));
        }
      }
    }
  }

  private void configureHttp1Pipeline(ChannelPipeline pipeline) {
    pipeline.addLast(new LoggingHandler(LogLevel.DEBUG));
    pipeline.addLast(new HttpClientCodec());
    pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
    pipeline.addLast(new MockServerHttpClientCodec(proxyConfiguration));
    pipeline.addLast(httpClientHandler);
    protocolFuture.complete(HttpProtocol.HTTP_1_1);
  }

  private void configureHttp2Pipeline(ChannelPipeline pipeline) {
    final Http2Connection connection = new DefaultHttp2Connection(false);
    final HttpToHttp2ConnectionHandlerBuilder http2ConnectionHandlerBuilder =
        new HttpToHttp2ConnectionHandlerBuilder()
            .frameListener(
                new DelegatingDecompressorFrameListener(
                    connection,
                    new InboundHttp2ToHttpAdapterBuilder(connection)
                        .maxContentLength(Integer.MAX_VALUE)
                        .propagateSettings(true)
                        .validateHttpHeaders(false)
                        .build()))
            .connection(connection)
            .flushPreface(true);
    if (log.isTraceEnabled()) {
      http2ConnectionHandlerBuilder.frameLogger(
          new Http2FrameLogger(LogLevel.TRACE, HttpClientHandler.class.getName()));
    }
    pipeline.addLast(http2ConnectionHandlerBuilder.build());
    pipeline.addLast(new Http2SettingsHandler(protocolFuture));
    pipeline.addLast(new MockServerHttpClientCodec(proxyConfiguration));
    pipeline.addLast(httpClientHandler);
  }

  private void configureBinaryPipeline(ChannelPipeline pipeline) {
    pipeline.addLast(new MockServerBinaryClientCodec());
    pipeline.addLast(binaryBridgeHandler);
    protocolFuture.complete(null);
  }

  private boolean isHostNotOnNoProxyHostList(InetSocketAddress remoteAddress) {
    if (remoteAddress == null || proxyConfiguration == null) {
      return true;
    }
    return proxyConfiguration.getNoProxyHosts().stream()
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
        .noneMatch(a -> remoteAddress.getHostName().equals(a.getHostName()));
  }
}
