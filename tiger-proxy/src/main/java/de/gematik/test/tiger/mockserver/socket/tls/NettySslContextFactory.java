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

package de.gematik.test.tiger.mockserver.socket.tls;

import de.gematik.test.tiger.mockserver.configuration.Configuration;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Slf4j
public class NettySslContextFactory {

  private final Configuration configuration;
  private final KeyAndCertificateFactory keyAndCertificateFactory;
  private final Map<String, SslContext> clientSslContexts = new ConcurrentHashMap<>();
  private SslContext serverSslContext = null;
  private final boolean forServer;

  public NettySslContextFactory(Configuration configuration, boolean forServer) {
    this.configuration = configuration;
    this.forServer = forServer;
    keyAndCertificateFactory = createKeyAndCertificateFactory();
    System.setProperty("https.protocols", configuration.tlsProtocols());
    configuration.nettySslContextFactoryCustomizer().accept(this);
  }

  public KeyAndCertificateFactory createKeyAndCertificateFactory() {
    if (configuration.customKeyAndCertificateFactorySupplier() != null) {
      return configuration
          .customKeyAndCertificateFactorySupplier()
          .buildKeyAndCertificateFactory(forServer, configuration);
    } else {
      throw new RuntimeException("No KeyAndCertificateFactorySupplier supplied!");
    }
  }

  public synchronized SslContext createClientSslContext(boolean enableHttp2) {
    String key = "enableHttp2=" + enableHttp2;
    SslContext clientSslContext = clientSslContexts.get(key);
    if (clientSslContext != null) {
      return clientSslContext;
    } else {
      return buildFreshClientSslContext(enableHttp2);
    }
  }

  private SslContext buildFreshClientSslContext(boolean enableHttp2) {
    try {
      // create x509 and private key if none exist yet
      if (keyAndCertificateFactory.certificateNotYetCreated()) {
        keyAndCertificateFactory.buildAndSavePrivateKeyAndX509Certificate();
      }
      SslContextBuilder sslContextBuilder =
          SslContextBuilder.forClient()
              .protocols(configuration.tlsProtocols().split(","))
              .keyManager(forwardProxyPrivateKey(), forwardProxyCertificateChain());
      if (enableHttp2) {
        configureALPN(sslContextBuilder);
      }
      sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
      var clientSslContext =
          buildClientSslContext(
              configuration.sslClientContextBuilderCustomizer().apply(sslContextBuilder));
      clientSslContexts.put("enableHttp2=" + enableHttp2, clientSslContext);
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

  private PrivateKey forwardProxyPrivateKey() {
    return keyAndCertificateFactory.privateKey();
  }

  private X509Certificate[] forwardProxyCertificateChain() {
    return keyAndCertificateFactory.certificateChain().toArray(new X509Certificate[0]);
  }

  public synchronized SslContext createServerSslContext() {
    if (serverSslContext != null
        // create x509 and private key if none exist yet
        && !keyAndCertificateFactory.certificateNotYetCreated()
        // re-create x509 and private key if SAN list has been updated and dynamic update has not
        // been disabled
        && (!configuration.rebuildServerTlsContext())) {
      return serverSslContext;
    }
    try {
      keyAndCertificateFactory.buildAndSavePrivateKeyAndX509Certificate();
      log.debug(
          "using certificate authority serial:{}issuer:{}subject:{}and certificate"
              + " serial:{}issuer:{}subject:{}",
          keyAndCertificateFactory.certificateAuthorityX509Certificate().getSerialNumber(),
          keyAndCertificateFactory.certificateAuthorityX509Certificate().getIssuerX500Principal(),
          keyAndCertificateFactory.certificateAuthorityX509Certificate().getSubjectX500Principal(),
          keyAndCertificateFactory.x509Certificate().getSerialNumber(),
          keyAndCertificateFactory.x509Certificate().getIssuerX500Principal(),
          keyAndCertificateFactory.x509Certificate().getSubjectX500Principal());
      final SslContextBuilder sslContextBuilder =
          SslContextBuilder.forServer(
                  keyAndCertificateFactory.privateKey(),
                  keyAndCertificateFactory.certificateChain())
              .protocols(configuration.tlsProtocols().split(","))
              .clientAuth(ClientAuth.OPTIONAL);
      configureALPN(sslContextBuilder);
      sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
      serverSslContext =
          configuration.sslServerContextBuilderCustomizer().apply(sslContextBuilder).build();
      configuration.rebuildServerTlsContext(false);
      return serverSslContext;
    } catch (RuntimeException | SSLException e) {
      log.error("Exception creating SSL context for server", e);
      throw new RuntimeException("exception creating SSL context for server", e);
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
