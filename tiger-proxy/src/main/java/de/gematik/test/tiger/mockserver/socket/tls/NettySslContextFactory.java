/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.socket.tls;

import de.gematik.test.tiger.mockserver.configuration.Configuration;
import de.gematik.test.tiger.mockserver.log.model.LogEntry;
import de.gematik.test.tiger.mockserver.logging.MockServerLogger;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.slf4j.event.Level;

/*
 * @author jamesdbloom
 */

/**
 * @author jamesdbloom
 */
public class NettySslContextFactory {

  private final Configuration configuration;
  private final MockServerLogger mockServerLogger;
  private final KeyAndCertificateFactory keyAndCertificateFactory;
  private final Map<String, SslContext> clientSslContexts = new ConcurrentHashMap<>();
  private SslContext serverSslContext = null;
  private final boolean forServer;

  public NettySslContextFactory(
      Configuration configuration, MockServerLogger mockServerLogger, boolean forServer) {
    this.configuration = configuration;
    this.mockServerLogger = mockServerLogger;
    this.forServer = forServer;
    keyAndCertificateFactory = createKeyAndCertificateFactory();
    System.setProperty("https.protocols", configuration.tlsProtocols());
    configuration.nettySslContextFactoryCustomizer().accept(this);
    if (configuration.proactivelyInitialiseTLS()) {
      createServerSslContext();
    }
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

  public synchronized SslContext createClientSslContext(
      boolean forwardProxyClient, boolean enableHttp2) {
    String key = "forwardProxyClient=" + forwardProxyClient + ",enableHttp2=" + enableHttp2;
    SslContext clientSslContext = clientSslContexts.get(key);
    if (clientSslContext != null && !configuration.rebuildTLSContext()) {
      return clientSslContext;
    } else {
      return buildFreshClientSslContext(forwardProxyClient, enableHttp2);
    }
  }

  private SslContext buildFreshClientSslContext(boolean forwardProxyClient, boolean enableHttp2) {
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
      if (forwardProxyClient) {
        switch (configuration.forwardProxyTLSX509CertificatesTrustManagerType()) {
          case ANY:
            sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
            break;
          case JVM:
            List<X509Certificate> mockServerX509Certificates = new ArrayList<>();
            mockServerX509Certificates.add(keyAndCertificateFactory.x509Certificate());
            mockServerX509Certificates.add(
                keyAndCertificateFactory.certificateAuthorityX509Certificate());
            sslContextBuilder.trustManager(jvmCAX509TrustCertificates(mockServerX509Certificates));
            break;
          case CUSTOM:
            sslContextBuilder.trustManager(customCAX509TrustCertificates());
            break;
        }
      } else {
        List<X509Certificate> mockServerX509Certificates = new ArrayList<>();
        mockServerX509Certificates.add(
            keyAndCertificateFactory.certificateAuthorityX509Certificate());
        sslContextBuilder.trustManager(jvmCAX509TrustCertificates(mockServerX509Certificates));
      }
      var clientSslContext =
          buildClientSslContext(
              configuration.sslClientContextBuilderCustomizer().apply(sslContextBuilder));
      clientSslContexts.put(
          "forwardProxyClient=" + forwardProxyClient + ",enableHttp2=" + enableHttp2,
          clientSslContext);
      configuration.rebuildTLSContext(false);
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

  private X509Certificate[] jvmCAX509TrustCertificates(
      List<X509Certificate> additionalX509Certificates)
      throws NoSuchAlgorithmException, KeyStoreException {
    TrustManagerFactory trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init((KeyStore) null);
    return Arrays.stream(trustManagerFactory.getTrustManagers())
        .filter(trustManager -> trustManager instanceof X509TrustManager)
        .flatMap(
            trustManager -> Arrays.stream(((X509TrustManager) trustManager).getAcceptedIssuers()))
        .collect(() -> additionalX509Certificates, List::add, List::addAll)
        .toArray(new X509Certificate[0]);
  }

  private X509Certificate[] customCAX509TrustCertificates() {
    ArrayList<X509Certificate> x509Certificates = new ArrayList<>();
    x509Certificates.add(keyAndCertificateFactory.x509Certificate());
    x509Certificates.add(keyAndCertificateFactory.certificateAuthorityX509Certificate());
    return x509Certificates.toArray(new X509Certificate[0]);
  }

  public synchronized SslContext createServerSslContext() {
    if (serverSslContext != null
        // create x509 and private key if none exist yet
        && !keyAndCertificateFactory.certificateNotYetCreated()
        // re-create x509 and private key if SAN list has been updated and dynamic update has not
        // been disabled
        && (!configuration.rebuildServerTlsContext()
            || configuration.preventCertificateDynamicUpdate())) {
      return serverSslContext;
    }
    try {
      keyAndCertificateFactory.buildAndSavePrivateKeyAndX509Certificate();
      mockServerLogger.logEvent(
          new LogEntry()
              .setLogLevel(Level.DEBUG)
              .setMessageFormat(
                  "using certificate authority serial:{}issuer:{}subject:{}and certificate serial:{}issuer:{}subject:{}")
              .setArguments(
                  keyAndCertificateFactory.certificateAuthorityX509Certificate().getSerialNumber(),
                  keyAndCertificateFactory
                      .certificateAuthorityX509Certificate()
                      .getIssuerX500Principal(),
                  keyAndCertificateFactory
                      .certificateAuthorityX509Certificate()
                      .getSubjectX500Principal(),
                  keyAndCertificateFactory.x509Certificate().getSerialNumber(),
                  keyAndCertificateFactory.x509Certificate().getIssuerX500Principal(),
                  keyAndCertificateFactory.x509Certificate().getSubjectX500Principal()));
      final SslContextBuilder sslContextBuilder =
          SslContextBuilder.forServer(
                  keyAndCertificateFactory.privateKey(),
                  keyAndCertificateFactory.certificateChain())
              .protocols(configuration.tlsProtocols().split(","))
              .clientAuth(
                  configuration.tlsMutualAuthenticationRequired()
                      ? ClientAuth.REQUIRE
                      : ClientAuth.OPTIONAL);
      configureALPN(sslContextBuilder);
      sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
      serverSslContext =
          configuration.sslServerContextBuilderCustomizer().apply(sslContextBuilder).build();
      configuration.rebuildServerTlsContext(false);
      return serverSslContext;
    } catch (Exception e) {
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
