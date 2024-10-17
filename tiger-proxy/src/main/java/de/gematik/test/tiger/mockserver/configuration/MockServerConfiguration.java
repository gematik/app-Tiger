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

package de.gematik.test.tiger.mockserver.configuration;

import de.gematik.test.tiger.mockserver.model.BinaryProxyListener;
import de.gematik.test.tiger.mockserver.socket.tls.KeyAndCertificateFactorySupplier;
import de.gematik.test.tiger.mockserver.socket.tls.NettySslContextFactory;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
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
  private BinaryProxyListener binaryProxyListener = null;
  private boolean enableTlsTermination = true;

  // proxy
  private InetSocketAddress forwardHttpProxy = null;
  private InetSocketAddress forwardHttpsProxy = null;
  private InetSocketAddress forwardSocksProxy = null;
  private String forwardProxyAuthenticationUsername = "";
  private String forwardProxyAuthenticationPassword = "";
  private String proxyAuthenticationRealm = "";
  private String proxyAuthenticationUsername = "";
  private String proxyAuthenticationPassword = "";
  private String noProxyHosts = "";

  // TLS
  private boolean rebuildServerTlsContext = false;
  private String tlsProtocols = "TLSv1,TLSv1.1,TLSv1.2";
  private KeyAndCertificateFactorySupplier customKeyAndCertificateFactorySupplier = null;
  private Function<SslContextBuilder, SslContext> clientSslContextBuilderFunction = null;
  private Consumer<NettySslContextFactory> nettySslContextFactoryCustomizer = factory -> {};
  private UnaryOperator<SslContextBuilder> sslServerContextBuilderCustomizer =
      UnaryOperator.identity();
  private UnaryOperator<SslContextBuilder> sslClientContextBuilderCustomizer =
      UnaryOperator.identity();
  private Function<java.security.cert.X509Certificate, byte[]> ocspResponseSupplier = null;
  private String masterSecretFile = null;

  // inbound - dynamic private key & x509
  private String sslCertificateDomainName = "localhost";
  private Set<String> sslSubjectAlternativeNameDomains = new ConcurrentSkipListSet<>(Set.of("localhost"));
  private Set<String> sslSubjectAlternativeNameIps = new ConcurrentSkipListSet<>(Set.of("127.0.0.1", "0.0.0.0"));

  public void addSubjectAlternativeName(String newSubjectAlternativeName) {
    sslSubjectAlternativeNameDomains.add(newSubjectAlternativeName);
    rebuildServerTlsContext = true;
  }
}
