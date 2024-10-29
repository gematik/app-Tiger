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

package de.gematik.test.tiger.mockserver.socket.tls;

import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.model.HttpProtocol;
import de.gematik.test.tiger.proxy.exceptions.TigerProxySslException;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;

/*
 * @author jamesdbloom
 */
@Slf4j
public class NettySslContextFactory {

  private final MockServerConfiguration configuration;
  private final KeyAndCertificateFactory keyAndCertificateFactory;
  private final Map<HttpProtocol, SslContext> clientSslContexts = new ConcurrentHashMap<>();
  private Pair<SslContext, TigerPkiIdentity> serverSslContextAndIdentity = null;
  private final boolean forServer;

  public NettySslContextFactory(MockServerConfiguration configuration, boolean forServer) {
    this.configuration = configuration;
    this.forServer = forServer;
    keyAndCertificateFactory = createKeyAndCertificateFactory();
    System.setProperty("https.protocols", configuration.tlsProtocols());
    configuration.nettySslContextFactoryCustomizer().accept(this);
  }

  public KeyAndCertificateFactory createKeyAndCertificateFactory() {
    if (configuration.customKeyAndCertificateFactorySupplier() == null) {
      throw new TigerProxySslException("No KeyAndCertificateFactorySupplier supplied!");
    }
    return configuration
        .customKeyAndCertificateFactorySupplier()
        .buildKeyAndCertificateFactory(forServer, configuration);
  }

  public synchronized SslContext createClientSslContext(Optional<HttpProtocol> protocol) {
    return createClientSslContext(protocol, null);
  }

  public synchronized SslContext createClientSslContext(
      Optional<HttpProtocol> protocol, String hostName) {
    return createClientSslContext(protocol.orElse(HttpProtocol.HTTP_1_1), hostName);
  }

  public synchronized SslContext createClientSslContext(HttpProtocol protocol, String hostName) {
    SslContext clientSslContext = clientSslContexts.get(protocol);
    if (clientSslContext != null) {
      return clientSslContext;
    } else {
      return buildFreshClientSslContext(protocol, hostName);
    }
  }

  private SslContext buildFreshClientSslContext(HttpProtocol protocol, String hostName) {
    try {
      val clientIdentity =
          keyAndCertificateFactory.buildAndSavePrivateKeyAndX509Certificate(hostName);
      // create x509 and private key if none exist yet
      SslContextBuilder sslContextBuilder =
          SslContextBuilder.forClient()
              .protocols(configuration.tlsProtocols().split(","))
              .keyManager(
                  clientIdentity.getPrivateKey(), clientIdentity.buildChainWithCertificate());
      if (protocol == HttpProtocol.HTTP_2) {
        configureALPN(sslContextBuilder);
      }
      sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
      var clientSslContext =
          buildClientSslContext(
              configuration.sslClientContextBuilderCustomizer().apply(sslContextBuilder));
      clientSslContexts.put(protocol, clientSslContext);
      return clientSslContext;
    } catch (Exception e) {
      throw new RuntimeException("Exception creating SSL context for client", e);
    }
  }

  private SslContext buildClientSslContext(SslContextBuilder builder) throws SSLException {
    if (configuration.clientSslContextBuilderFunction() == null) {
      return builder.build();
    } else {
      return configuration.clientSslContextBuilderFunction().apply(builder);
    }
  }

  public synchronized Pair<SslContext, TigerPkiIdentity> createServerSslContext(String hostname) {
    if (serverSslContextAndIdentity != null
        // re-create x509 and private key if SAN list has been updated and dynamic update has not
        // been disabled
        && (!configuration.rebuildServerTlsContext())) {
      log.info("Using existing server SSL context for {}", hostname);
      return serverSslContextAndIdentity;
    }
    log.info("Creating new server SSL context for {}", hostname);
    try {
      val serverIdentity =
          keyAndCertificateFactory.buildAndSavePrivateKeyAndX509Certificate(hostname);

      log.atInfo()
          .addArgument(() -> serverIdentity.getCertificate().getSubjectX500Principal())
          .addArgument(() -> serverIdentity.getCertificate().getIssuerX500Principal())
          .log("Using Server Certificate '{}', issued by '{}'");
      final SslContextBuilder sslContextBuilder =
          SslContextBuilder.forServer(
                  serverIdentity.getPrivateKey(), serverIdentity.buildChainWithCertificate())
              .protocols(configuration.tlsProtocols().split(","))
              .clientAuth(ClientAuth.OPTIONAL);
      configureALPN(sslContextBuilder);
      sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
      val serverContext =
          configuration.sslServerContextBuilderCustomizer().apply(sslContextBuilder).build();
      serverSslContextAndIdentity = Pair.of(serverContext, serverIdentity);
      configuration.rebuildServerTlsContext(false);
      return serverSslContextAndIdentity;
    } catch (RuntimeException | SSLException e) {
      log.error("Exception creating SSL context for server", e);
      throw new TigerProxySslException("exception creating SSL context for server", e);
    }
  }

  private static void configureALPN(SslContextBuilder sslContextBuilder) {
    Consumer<SslContextBuilder> configureALPN =
        contextBuilder ->
            contextBuilder
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .applicationProtocolConfig(
                    new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK
                        // providers.
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        // ACCEPT is currently the only mode supported by both OpenSsl and JDK
                        // providers.
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2,
                        ApplicationProtocolNames.HTTP_1_1));
    if (SslProvider.isAlpnSupported(SslContext.defaultServerProvider())) {
      configureALPN.accept(sslContextBuilder.sslProvider(SslContext.defaultServerProvider()));
    } else if (SslProvider.isAlpnSupported(SslProvider.JDK)) {
      configureALPN.accept(sslContextBuilder.sslProvider(SslProvider.JDK));
    } else if (SslProvider.isAlpnSupported(SslProvider.OPENSSL)) {
      configureALPN.accept(sslContextBuilder.sslProvider(SslProvider.OPENSSL));
    }
  }
}
