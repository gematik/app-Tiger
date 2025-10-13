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
package de.gematik.test.tiger.mockserver.configuration;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration;
import de.gematik.test.tiger.mockserver.socket.tls.KeyAlgorithmPreference;
import de.gematik.test.tiger.mockserver.socket.tls.KeyAndCertificateFactory;
import de.gematik.test.tiger.mockserver.socket.tls.NettySslContextFactory;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyRoutingException;
import de.gematik.test.tiger.proxy.handler.BinaryExchangeHandler;
import de.gematik.test.tiger.proxy.handler.RbelBinaryModifierPlugin;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import lombok.Data;
import lombok.experimental.Accessors;

/*
 * @author jamesdbloom
 */
@Data
@Accessors(fluent = true)
public class MockServerConfiguration {

  public static MockServerConfiguration configuration() {
    return new MockServerConfiguration();
  }

  // general
  private String mockServerName = null;
  private RbelConverter rbelConverter = null;

  // scalability
  private Integer nioEventLoopThreadCount = 5;
  private Integer actionHandlerThreadCount =
      Math.max(5, Runtime.getRuntime().availableProcessors());
  private Integer clientNioEventLoopThreadCount = 5;
  private Integer webSocketClientEventLoopThreadCount = 5;
  private Long maxFutureTimeoutInMillis = TimeUnit.SECONDS.toMillis(90);

  // socket
  private Long maxSocketTimeoutInMillis = TimeUnit.SECONDS.toMillis(20);
  private Long socketConnectionTimeoutInMillis = TimeUnit.SECONDS.toMillis(20);
  private boolean alwaysCloseSocketConnections = false;

  // http request parsing
  private Integer maxInitialLineLength = Integer.MAX_VALUE;
  private Integer maxHeaderSize = Integer.MAX_VALUE;
  private Integer maxChunkSize = Integer.MAX_VALUE;

  // non http proxying
  private BinaryExchangeHandler binaryProxyListener = null;
  private boolean enableTlsTermination = true;
  private List<RbelBinaryModifierPlugin> binaryModifierPlugins;

  // callbacks
  private BiConsumer<TigerProxyRoutingException, ChannelHandlerContext> exceptionHandlingCallback =
      (x, y) -> {};

  // proxy
  private ProxyConfiguration proxyConfiguration = null;
  private InetSocketAddress directForwarding = null;

  // TLS
  private boolean rebuildServerTlsContext = false;
  private String tlsProtocols = "TLSv1,TLSv1.1,TLSv1.2";
  private KeyAndCertificateFactory serverKeyAndCertificateFactory = null;
  private KeyAndCertificateFactory clientKeyAndCertificateFactory = null;
  private Function<SslContextBuilder, SslContext> clientSslContextBuilderFunction = null;
  private Consumer<NettySslContextFactory> nettySslContextFactoryCustomizer = factory -> {};
  private UnaryOperator<SslContextBuilder> sslServerContextBuilderCustomizer =
      UnaryOperator.identity();
  private UnaryOperator<SslContextBuilder> sslClientContextBuilderCustomizer =
      UnaryOperator.identity();
  private Function<java.security.cert.X509Certificate, byte[]> ocspResponseSupplier = null;
  private String masterSecretFile = null;
  private KeyAlgorithmPreference keyAlgorithmPreference = KeyAlgorithmPreference.MIXED;

  // inbound - dynamic private key & x509
  private String sslCertificateDomainName = "localhost";
  private Set<String> sslSubjectAlternativeNameDomains =
      new ConcurrentSkipListSet<>(Set.of("localhost"));
  private Set<String> sslSubjectAlternativeNameIps =
      new ConcurrentSkipListSet<>(Set.of("127.0.0.1", "0.0.0.0"));

  public void addSubjectAlternativeName(String newSubjectAlternativeName) {
    sslSubjectAlternativeNameDomains.add(newSubjectAlternativeName);
    rebuildServerTlsContext = true;
  }
}
