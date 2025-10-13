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
  private final Map<Pair<HttpProtocol, String>, SslContext> clientSslContexts =
      new ConcurrentHashMap<>();
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
    if (forServer) {
      if (configuration.serverKeyAndCertificateFactory() == null) {
        throw new TigerProxySslException("No serverKeyAndCertificateFactory found!");
      }
      return configuration.serverKeyAndCertificateFactory();
    } else {
      if (configuration.clientKeyAndCertificateFactory() == null) {
        throw new TigerProxySslException("No clientKeyAndCertificateFactory found!");
      }
      return configuration.clientKeyAndCertificateFactory();
    }
  }

  public synchronized SslContext createClientSslContext(Optional<HttpProtocol> protocol) {
    return createClientSslContext(protocol, null);
  }

  public synchronized SslContext createClientSslContext(
      Optional<HttpProtocol> protocol, String hostName) {
    return createClientSslContext(protocol.orElse(HttpProtocol.HTTP_1_1), hostName);
  }

  public synchronized SslContext createClientSslContext(HttpProtocol protocol, String hostName) {
    SslContext clientSslContext = clientSslContexts.get(Pair.of(protocol, hostName));
    if (clientSslContext != null) {
      return clientSslContext;
    } else {
      return buildFreshClientSslContext(protocol, hostName);
    }
  }

  private SslContext buildFreshClientSslContext(HttpProtocol protocol, String hostName) {
    try {
      val clientIdentity =
          keyAndCertificateFactory.resolveIdentityForHostname(
              hostName, KeyAlgorithmPreference.MIXED);
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
      clientSslContexts.put(Pair.of(protocol, hostName), clientSslContext);
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

  public synchronized Pair<SslContext, TigerPkiIdentity> createServerSslContext(
      String hostname, KeyAlgorithmPreference clientAlgorithmPreference) {
    val algorithmPreference =
        KeyAlgorithmPreference.determineEffectivePreference(
            clientAlgorithmPreference, configuration.keyAlgorithmPreference());
    if (serverSslContextAndIdentity != null
        // re-create x509 and private key if SAN list has been updated and dynamic update has not
        // been disabled
        && (!configuration.rebuildServerTlsContext())
        && keyPreferenceMatches(algorithmPreference)) {
      log.info(
          "Using existing server SSL context for {} with key-algorithm {}",
          hostname,
          serverSslContextAndIdentity.getValue().getPrivateKey().getAlgorithm());
      return serverSslContextAndIdentity;
    }
    log.info("Creating new server SSL context for {}", hostname);
    try {
      val serverIdentity =
          keyAndCertificateFactory.resolveIdentityForHostname(hostname, algorithmPreference);

      log.atInfo()
          .addArgument(() -> serverIdentity.getPrivateKey().getAlgorithm())
          .addArgument(() -> serverIdentity.getCertificate().getSubjectX500Principal())
          .addArgument(() -> serverIdentity.getCertificate().getIssuerX500Principal())
          .log("Using {} Server Certificate '{}', issued by '{}'");
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

  private boolean keyPreferenceMatches(KeyAlgorithmPreference algorithmPreference) {
    if (algorithmPreference == KeyAlgorithmPreference.MIXED
        || algorithmPreference == KeyAlgorithmPreference.UNKNOWN) {
      return true;
    } else {
      val serverIdentity = serverSslContextAndIdentity.getValue();
      if (serverIdentity == null) {
        return false;
      } else {
        return algorithmPreference.matches(serverIdentity);
      }
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
                        //                        ApplicationProtocolNames.HTTP_2, //TODO TGR-1699
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
