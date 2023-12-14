/*
 * Copyright (c) 2023 gematik GmbH
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

package de.gematik.test.tiger.proxy;

import static de.gematik.test.tiger.proxy.tls.TlsCertificateGenerator.generateNewCaCertificate;
import static org.mockserver.model.HttpRequest.request;

import de.gematik.test.tiger.common.config.RbelModificationDescription;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerTlsConfiguration;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import de.gematik.test.tiger.proxy.configuration.ProxyConfigurationConverter;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyConfigurationException;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyRouteConflictException;
import de.gematik.test.tiger.proxy.exceptions.TigerProxySslException;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyStartupException;
import de.gematik.test.tiger.proxy.handler.BinaryExchangeHandler;
import de.gematik.test.tiger.proxy.handler.ForwardAllCallback;
import de.gematik.test.tiger.proxy.handler.ForwardProxyCallback;
import de.gematik.test.tiger.proxy.handler.ReverseProxyCallback;
import de.gematik.test.tiger.proxy.tls.DynamicTigerKeyAndCertificateFactory;
import de.gematik.test.tiger.proxy.tls.StaticTigerKeyAndCertificateFactory;
import io.netty.handler.ssl.SslProvider;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javax.net.ssl.*;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import kong.unirest.apache.ApacheClient;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.tomcat.util.buf.UriUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.Configuration;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.ExpectationId;
import org.mockserver.netty.MockServer;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager;
import org.mockserver.socket.tls.KeyAndCertificateFactoryFactory;
import org.mockserver.socket.tls.KeyAndCertificateFactorySupplier;
import org.mockserver.socket.tls.NettySslContextFactory;
import org.mockserver.socket.tls.bouncycastle.BCKeyAndCertificateFactory;
import org.springframework.stereotype.Component;

@Component
@EqualsAndHashCode(callSuper = true)
public class TigerProxy extends AbstractTigerProxy implements AutoCloseable {

  private static final String CA_CERT_ALIAS = "caCert";
  private static final String SSL_KEY_MANAGER_FACTORY_ALGORITHM = "ssl.KeyManagerFactory.algorithm";
  private static final String JDK_TLS_NAMED_GROUPS = "jdk.tls.namedGroups";
  private final List<DynamicTigerKeyAndCertificateFactory> tlsFactories = new ArrayList<>();
  private final List<Consumer<Throwable>> exceptionListeners = new ArrayList<>();
  @Getter private final MockServerToRbelConverter mockServerToRbelConverter;
  private final Map<String, TigerRoute> tigerRouteMap = new HashMap<>();
  private final List<TigerRemoteProxyClient> remoteProxyClients = new ArrayList<>();

  /**
   * Tiger Proxy health endpoint performs http get requests towards the local server port of the
   * Tiger Proxy. To filter them out from Rbel logs we add a specific query param
   * (healthEndPointUuid) with this uuid as value. The Filtering takes place in {@link
   * de.gematik.test.tiger.proxy.handler.AbstractTigerRouteCallback}.
   */
  @Getter private final UUID healthEndpointRequestUuid = UUID.randomUUID();

  private MockServer mockServer;
  private MockServerClient mockServerClient;
  private TigerPkiIdentity generatedRootCa;

  public TigerProxy(final TigerProxyConfiguration configuration) {
    super(configuration);

    ensureCorrectJvmSecurityProviderInitialization();

    mockServerToRbelConverter = new MockServerToRbelConverter(getRbelLogger().getRbelConverter());
    bootMockServer();

    if (!configuration.isSkipTrafficEndpointsSubscription()) {
      subscribeToTrafficEndpoints(configuration);
    }

    if (configuration.getModifications() != null) {
      int counter = 0;
      for (final RbelModificationDescription modification : configuration.getModifications()) {
        if (modification.getName() == null) {
          modification.setName("TigerModification #" + counter++);
        }
        getRbelLogger().getRbelModifier().addModification(modification);
      }
    }
  }

  private static synchronized void ensureCorrectJvmSecurityProviderInitialization() {
    if ("PKIX".equals(Security.getProperty(SSL_KEY_MANAGER_FACTORY_ALGORITHM))) {
      return;
    }
    Security.setProperty(SSL_KEY_MANAGER_FACTORY_ALGORITHM, "PKIX");
    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
    Security.insertProviderAt(new BouncyCastleProvider(), 1);
    Security.removeProvider(BouncyCastleJsseProvider.PROVIDER_NAME);
    Security.insertProviderAt(new BouncyCastleJsseProvider(), 2);
  }

  private static void customizeServerBuilderCustomizer(TigerTlsConfiguration tlsConfiguration) {
    if (tlsConfiguration.getServerSslSuites() != null
        || tlsConfiguration.getServerTlsProtocols() != null) {
      NettySslContextFactory.sslServerContextBuilderCustomizer = // NOSONAR
          builder -> {
            if (tlsConfiguration.getServerSslSuites() != null) {
              builder.ciphers(tlsConfiguration.getServerSslSuites());
            }
            if (tlsConfiguration.getServerTlsProtocols() != null) {
              builder.protocols(tlsConfiguration.getServerTlsProtocols());
            }
            builder.sslProvider(SslProvider.JDK);

            return builder;
          };
    } else {
      NettySslContextFactory.sslServerContextBuilderCustomizer = // NOSONAR
          b -> {
            b.sslProvider(SslProvider.JDK);
            return b;
          };
    }
  }

  private static void customizeClientBuilderCustomizer(TigerTlsConfiguration tlsConfiguration) {
    if (tlsConfiguration.getClientSslSuites() != null) {
      NettySslContextFactory.sslClientContextBuilderCustomizer = // NOSONAR
          builder -> {
            builder.ciphers(tlsConfiguration.getClientSslSuites());
            builder.sslProvider(SslProvider.JDK);
            return builder;
          };
    } else {
      NettySslContextFactory.sslClientContextBuilderCustomizer = // NOSONAR
          b -> {
            b.sslProvider(SslProvider.JDK);
            return b;
          };
    }
  }

  private static URL buildUrlSafe(TigerRoute tigerRoute) {
    try {
      return new URL(tigerRoute.getFrom());
    } catch (MalformedURLException e) {
      throw new TigerProxyStartupException("Error while building route", e);
    }
  }

  private static CloseableHttpClient getHttpClient(SSLContext sslContext) {
    return HttpClients.custom()
        .setSSLContext(sslContext)
        .setSSLHostnameVerifier(new DefaultHostnameVerifier())
        .build();
  }

  private static SSLContext tryGetSslContext(SSLContext sslContext) {
    try (CloseableHttpClient httpClient = getHttpClient(sslContext);
        UnirestInstance unirestInstance = Unirest.primaryInstance()) {
      unirestInstance.config().httpClient(config -> ApacheClient.builder(httpClient).apply(config));
      return sslContext;
    } catch (IOException e) {
      throw new TigerProxyTrustManagerBuildingException(
          "Error while building HTTP Client for Tiger Proxy", e);
    }
  }

  private static void setSslServerContextBuilderCustomizer() {
    NettySslContextFactory.sslServerContextBuilderCustomizer = UnaryOperator.identity();
  }

  /**
   * Will restart the internal mockserver. This can be done to force a reload of the configured
   * TLS-identities. This will block until the mockserver is running again. Background connections
   * will most likely be interrupted. Routes will be re-added to the server, but the ID's will
   * change.
   */
  public void restartMockserver() {
    if (getTigerProxyConfiguration().getProxyPort() == null) {
      getTigerProxyConfiguration().setProxyPort(mockServer.getLocalPort());
    }
    mockServer.stop();
    mockServerClient.stop();
    var originalRoutes = Collections.unmodifiableMap(tigerRouteMap);
    tigerRouteMap.clear();
    bootMockServer();
    originalRoutes.values().stream()
        .filter(r -> !r.isInternalRoute())
        .forEach(
            r -> {
              try {
                addRoute(r);
              } catch (RuntimeException e) {
                // swallow
                log.trace("Ignored exception during re-adding of routes", e);
              }
            });
  }

  private void bootMockServer() {
    createNewMockServer();
    buildMockServerClient();
  }

  private void buildMockServerClient() {
    mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort());

    if (getTigerProxyConfiguration().isActivateForwardAllLogging()) {
      mockServerClient
          .when(
              request().withPath(".*"),
              Times.unlimited(),
              TimeToLive.unlimited(),
              Integer.MIN_VALUE)
          .forward(new ForwardAllCallback(this));
    }
    addRoutesToTigerProxy();
  }

  private void createNewMockServer() {
    KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(
        buildKeyAndCertificateFactory());

    Configuration mockServerConfiguration = Configuration.configuration();
    mockServerConfiguration.forwardProxyTLSX509CertificatesTrustManagerType(
        ForwardProxyTLSX509CertificatesTrustManager.ANY);
    mockServerConfiguration.maxLogEntries(0);
    if (StringUtils.isNotEmpty(getTigerProxyConfiguration().getProxyLogLevel())) {
      mockServerConfiguration.logLevel(getTigerProxyConfiguration().getProxyLogLevel());
    } else {
      mockServerConfiguration.logLevel("WARN");
    }

    customizeSslSuitesIfApplicable();

    final Optional<ProxyConfiguration> forwardProxyConfig =
        ProxyConfigurationConverter.convertForwardProxyConfigurationToMockServerConfiguration(
            getTigerProxyConfiguration());
    outputForwardProxyConfigLogs(forwardProxyConfig);

    if (getTigerProxyConfiguration().getDirectReverseProxy() == null) {
      mockServer =
          forwardProxyConfig
              .map(
                  proxyConfiguration ->
                      new MockServer(
                          proxyConfiguration, getTigerProxyConfiguration().getPortAsArray()))
              .orElseGet(() -> new MockServer(getTigerProxyConfiguration().getPortAsArray()));
    } else {
      mockServer = spawnDirectInverseTigerProxy(mockServerConfiguration, forwardProxyConfig);
    }
    String proxyName = getName().orElse("?");
    log.info("Proxy '{}' started on port {}", proxyName, mockServer.getLocalPort());
  }

  private void addRoutesToTigerProxy() {
    if (getTigerProxyConfiguration().getProxyRoutes() != null) {
      for (final TigerRoute tigerRoute : getTigerProxyConfiguration().getProxyRoutes()) {
        addRoute(tigerRoute);
      }
    }
  }

  private MockServer spawnDirectInverseTigerProxy(
      Configuration mockServerConfiguration, Optional<ProxyConfiguration> forwardProxyConfig) {
    mockServerConfiguration.forwardBinaryRequestsWithoutWaitingForResponse(true);
    mockServerConfiguration.binaryProxyListener(new BinaryExchangeHandler(this));
    if (forwardProxyConfig.isPresent()) {
      throw new TigerProxyStartupException(
          "DirectForwardProxy configured with additional forwardProxy: Not possible! (forwardProxy"
              + " is always HTTP!)");
    }
    MockServer newMockServer =
        new MockServer(
            mockServerConfiguration,
            getTigerProxyConfiguration().getDirectReverseProxy().getPort(),
            getTigerProxyConfiguration().getDirectReverseProxy().getHostname(),
            getTigerProxyConfiguration().getPortAsArray());
    addReverseProxyRouteIfNotPresent();
    return newMockServer;
  }

  private void addReverseProxyRouteIfNotPresent() {
    if (getTigerProxyConfiguration().getProxyRoutes() == null) {
      getTigerProxyConfiguration().setProxyRoutes(new ArrayList<>());
    }
    getTigerProxyConfiguration()
        .getProxyRoutes()
        .add(
            TigerRoute.builder()
                .from("/")
                .to(
                    "http://"
                        + getTigerProxyConfiguration().getDirectReverseProxy().getHostname()
                        + ":"
                        + getTigerProxyConfiguration().getDirectReverseProxy().getPort())
                .build());
  }

  private void customizeSslSuitesIfApplicable() {
    final TigerTlsConfiguration tlsConfiguration = getTigerProxyConfiguration().getTls();

    customizeServerBuilderCustomizer(tlsConfiguration);

    customizeClientBuilderCustomizer(tlsConfiguration);

    customizeClientBuilderFunction(tlsConfiguration);
  }

  private void customizeClientBuilderFunction(TigerTlsConfiguration tlsConfiguration) {
    if (tlsConfiguration.getClientSupportedGroups() != null
        && !tlsConfiguration.getClientSupportedGroups().isEmpty()) {
      NettySslContextFactory.clientSslContextBuilderFunction = // NOSONAR
          sslContextBuilder -> {
            String before = System.getProperty(JDK_TLS_NAMED_GROUPS);
            try {
              System.setProperty(
                  JDK_TLS_NAMED_GROUPS,
                  String.join(",", tlsConfiguration.getClientSupportedGroups()));
              sslContextBuilder.sslProvider(SslProvider.JDK);
              return sslContextBuilder.build();
            } catch (SSLException e) {
              throw new TigerProxySslException(
                  "Error while building SSL context in Tiger-Proxy " + getName().orElse(""), e);
            } finally {
              if (before != null) {
                System.setProperty(JDK_TLS_NAMED_GROUPS, before);
              } else {
                System.clearProperty(JDK_TLS_NAMED_GROUPS);
              }
            }
          };
    }
  }

  private KeyAndCertificateFactorySupplier buildKeyAndCertificateFactory() {
    return (mockServerLogger, isServerInstance, mockServerConfiguration) -> {
      if (isServerInstance) {
        if (getTigerProxyConfiguration().getTls() != null
            && getTigerProxyConfiguration().getTls().getServerIdentity() != null) {
          return new StaticTigerKeyAndCertificateFactory(
              mockServerLogger,
              getTigerProxyConfiguration(),
              getTigerProxyConfiguration().getTls().getServerIdentity());
        } else {
          final DynamicTigerKeyAndCertificateFactory dynamicTigerKeyAndCertificateFactory =
              new DynamicTigerKeyAndCertificateFactory(
                  mockServerLogger,
                  getTigerProxyConfiguration(),
                  determineServerRootCa()
                      .orElseThrow(
                          () -> new TigerProxyStartupException("Unrecoverable TLS startup state")),
                  mockServerConfiguration);
          this.tlsFactories.add(dynamicTigerKeyAndCertificateFactory);
          return dynamicTigerKeyAndCertificateFactory;
        }
      } else {
        if (getTigerProxyConfiguration().getTls() != null
            && getTigerProxyConfiguration().getTls().getForwardMutualTlsIdentity() != null) {
          return new StaticTigerKeyAndCertificateFactory(
              mockServerLogger,
              getTigerProxyConfiguration(),
              getTigerProxyConfiguration().getTls().getForwardMutualTlsIdentity());
        } else {
          return new BCKeyAndCertificateFactory(mockServerConfiguration, mockServerLogger);
        }
      }
    };
  }

  private Optional<TigerPkiIdentity> determineServerRootCa() {
    if (getTigerProxyConfiguration().getTls().getServerRootCa() != null) {
      return Optional.of(getTigerProxyConfiguration().getTls().getServerRootCa());
    } else {
      if (generatedRootCa == null) {
        generatedRootCa = generateNewCaCertificate();
      }
      return Optional.of(generatedRootCa);
    }
  }

  public void subscribeToTrafficEndpoints(final TigerProxyConfiguration configuration) {
    Optional.of(configuration)
        .map(TigerProxyConfiguration::getTrafficEndpoints)
        .ifPresent(this::subscribeToTrafficEndpoints);
  }

  public void subscribeToTrafficEndpoints(final List<String> trafficEndpointUrls) {
    Optional.of(trafficEndpointUrls).stream()
        .flatMap(List::stream)
        .parallel()
        .map(
            url ->
                new TigerRemoteProxyClient(
                    url,
                    TigerProxyConfiguration.builder()
                        .downloadInitialTrafficFromEndpoints(
                            getTigerProxyConfiguration().isDownloadInitialTrafficFromEndpoints())
                        .trafficEndpointFilterString(
                            getTigerProxyConfiguration().getTrafficEndpointFilterString())
                        .name(getTigerProxyConfiguration().getName())
                        .connectionTimeoutInSeconds(
                            getTigerProxyConfiguration().getConnectionTimeoutInSeconds())
                        .build(),
                    this))
        .forEach(remoteProxyClients::add);

    remoteProxyClients.parallelStream().forEach(TigerRemoteProxyClient::connect);
  }

  @Override
  public String getBaseUrl() {
    return "http://localhost:" + mockServer.getLocalPort();
  }

  @Override
  public int getProxyPort() {
    return mockServer.getLocalPort();
  }

  public int getAdminPort() {
    return getTigerProxyConfiguration().getAdminPort();
  }

  @Override
  public List<TigerRoute> getRoutes() {
    return tigerRouteMap.values().stream().toList();
  }

  @Override
  public RbelModificationDescription addModificaton(
      final RbelModificationDescription modification) {
    getRbelLogger().getRbelModifier().addModification(modification);
    return modification;
  }

  @Override
  public List<RbelModificationDescription> getModifications() {
    return getRbelLogger().getRbelModifier().getModifications();
  }

  @Override
  public void removeModification(final String modificationId) {
    getRbelLogger().getRbelModifier().deleteModification(modificationId);
  }

  @Override
  public synchronized TigerRoute addRoute(final TigerRoute tigerRoute) {
    assertThatRouteIsUnique(tigerRoute);

    log.info("Adding route {} -> {}", tigerRoute.getFrom(), tigerRoute.getTo());
    final Expectation[] expectations = buildRouteAndReturnExpectation(tigerRoute);
    if (expectations.length > 1) {
      log.warn(
          "Unexpected number of expectations created! Got {}, expected 1", expectations.length);
    }
    if (expectations.length == 0) {
      throw new TigerProxyConfigurationException(
          "Error while adding route from '{}' to '{}': Got 0 new expectations");
    }

    final TigerRoute createdTigerRoute = tigerRoute.withId(expectations[0].getId());
    tigerRouteMap.put(expectations[0].getId(), createdTigerRoute);

    log.info("Created route {} with expectation {}", createdTigerRoute, expectations[0]);
    return createdTigerRoute;
  }

  private void assertThatRouteIsUnique(TigerRoute tigerRoute) {
    tigerRouteMap.values().stream()
        .filter(
            existingRoute ->
                uriTwoIsBelowUriOne(existingRoute.getFrom(), tigerRoute.getFrom())
                    || uriTwoIsBelowUriOne(tigerRoute.getFrom(), existingRoute.getFrom()))
        .findAny()
        .ifPresent(
            existingRoute -> {
              throw new TigerProxyRouteConflictException(existingRoute);
            });
  }

  private boolean uriTwoIsBelowUriOne(final String value1, final String value2) {
    try {
      final URI uri1 = new URI(value1);
      final URI uri2WithUri1Scheme = new URIBuilder(value2).setScheme(uri1.getScheme()).build();
      return !new URI(value1).relativize(uri2WithUri1Scheme).equals(uri2WithUri1Scheme);
    } catch (final URISyntaxException e) {
      return false;
    }
  }

  private Expectation[] buildRouteAndReturnExpectation(final TigerRoute tigerRoute) {
    if (UriUtil.hasScheme(tigerRoute.getFrom())) {
      return buildForwardProxyRoute(tigerRoute);
    } else {
      return buildReverseProxyRoute(tigerRoute);
    }
  }

  private Expectation[] buildReverseProxyRoute(final TigerRoute tigerRoute) {
    return mockServerClient
        .when(request().withPath(tigerRoute.getFrom() + ".*"))
        .forward(new ReverseProxyCallback(this, tigerRoute));
  }

  private Expectation[] buildForwardProxyRoute(final TigerRoute tigerRoute) {
    final URL url = buildUrlSafe(tigerRoute);
    return mockServerClient
        .when(
            request()
                .withHeader("Host", url.getAuthority())
                .withSecure(url.getProtocol().equals("https")))
        .forward(new ForwardProxyCallback(this, tigerRoute));
  }

  /**
   * Adds the given host to the list of alternative names in the tiger proxy TLS certificate. Adding
   * alternative names after an initial request has been handled by the tiger proxy will have no
   * effect, since the internal mockserver will have already created its SSLContext. If this is
   * required, then a restart of the internal mockserver is necessary. See {@link
   * #restartMockserver()}
   *
   * @param host the host to add as alternative name.
   */
  public void addAlternativeName(final String host) {
    if (StringUtils.isBlank(host)) {
      return;
    }
    final List<String> newAlternativeNames = new ArrayList<>();
    if (getTigerProxyConfiguration().getTls() != null
        && getTigerProxyConfiguration().getTls().getAlternativeNames() != null) {
      newAlternativeNames.addAll(getTigerProxyConfiguration().getTls().getAlternativeNames());
    }
    newAlternativeNames.add(host);
    Objects.requireNonNull(getTigerProxyConfiguration().getTls())
        .setAlternativeNames(newAlternativeNames);

    for (final DynamicTigerKeyAndCertificateFactory tlsFactory : tlsFactories) {
      tlsFactory.addAlternativeName(host);
      tlsFactory.resetEeCertificate();
    }
  }

  @Override
  public void removeRoute(final String routeId) {
    if (!mockServer.isRunning()) {
      return;
    }
    mockServerClient.clear(new ExpectationId().withId(routeId));
    final TigerRoute route = tigerRouteMap.remove(routeId);

    log.info(
        "Deleted route {}. Current # expectations {}",
        route,
        mockServerClient.retrieveActiveExpectations(request()).length);
  }

  public SSLContext getConfiguredTigerProxySslContext() {
    try {
      final SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new TrustManager[] {buildTrustManagerForTigerProxy()}, null);

      SSLContext.setDefault(sslContext);
      return sslContext;
    } catch (final Exception e) {
      throw new TigerProxyTrustManagerBuildingException(
          "Error while configuring SSL Context for Tiger Proxy", e);
    }
  }

  public X509TrustManager buildTrustManagerForTigerProxy() {
    try {
      final X509TrustManager defaultTrustManager = extractTrustManager(null);
      final X509TrustManager customTrustManager = extractTrustManager(buildTruststore());
      return new X509TrustManager() {
        @Override
        public X509Certificate[] getAcceptedIssuers() {
          return defaultTrustManager.getAcceptedIssuers();
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] chain, final String authType)
            throws CertificateException {
          try {
            customTrustManager.checkServerTrusted(chain, authType);
          } catch (final CertificateException e) {
            defaultTrustManager.checkServerTrusted(chain, authType);
          }
        }

        @Override
        public void checkClientTrusted(final X509Certificate[] chain, final String authType)
            throws CertificateException {
          defaultTrustManager.checkClientTrusted(chain, authType);
        }
      };
    } catch (final Exception e) {
      throw new TigerProxyTrustManagerBuildingException(
          "Error while building TrustManager for Tiger Proxy", e);
    }
  }

  private X509TrustManager extractTrustManager(final KeyStore keystore)
      throws NoSuchAlgorithmException, KeyStoreException {
    final TrustManagerFactory trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(keystore);

    return (X509TrustManager)
        Arrays.stream(trustManagerFactory.getTrustManagers())
            .filter(X509TrustManager.class::isInstance)
            .findAny()
            .orElseThrow(
                () ->
                    new TigerProxyTrustManagerBuildingException(
                        "Error while configuring TrustManager for Tiger Proxy"));
  }

  public KeyStore buildTruststore() {
    try {
      final KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
      ks.load(null);
      final TigerPkiIdentity serverIdentity =
          determineServerRootCa()
              .or(
                  () ->
                      Optional.ofNullable(getTigerProxyConfiguration().getTls())
                          .map(TigerTlsConfiguration::getServerIdentity))
              .orElseThrow(
                  () ->
                      new TigerProxyTrustManagerBuildingException(
                          "Unrecoverable state: Server-Identity null and Server-CA empty"));

      ks.setCertificateEntry(CA_CERT_ALIAS, serverIdentity.getCertificate());
      int chainCertCtr = 0;
      for (final X509Certificate chainCert : serverIdentity.getCertificateChain()) {
        ks.setCertificateEntry("chainCert" + chainCertCtr++, chainCert);
      }
      return ks;
    } catch (final Exception e) {
      throw new TigerProxyTrustManagerBuildingException(
          "Error while building SSL-Context for Tiger Proxy", e);
    }
  }

  public SSLContext buildSslContext() {
    try {
      TrustManagerFactory tmf =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(buildTruststore());
      SSLContext sslContext = SSLContext.getInstance("TLS");
      TrustManager[] trustManagers = tmf.getTrustManagers();
      sslContext.init(null, trustManagers, null);
      return tryGetSslContext(sslContext);
    } catch (RuntimeException
        | NoSuchAlgorithmException
        | KeyStoreException
        | KeyManagementException e) {
      throw new TigerProxyTrustManagerBuildingException(
          "Error while building SSL-Context for Tiger Proxy", e);
    }
  }

  private void outputForwardProxyConfigLogs(final Optional<ProxyConfiguration> forwardProxyConfig) {
    if (forwardProxyConfig.isEmpty()) {
      log.info("Tigerproxy has NO forward proxy configured!");
    } else {
      final ProxyConfiguration configNotEmpty = forwardProxyConfig.get();
      if (configNotEmpty.getUsername() == null) {
        log.info(
            "Forward proxy is set to {}://{}:{}",
            configNotEmpty.getType(),
            configNotEmpty.getProxyAddress().getHostName(),
            configNotEmpty.getProxyAddress().getPort());
      } else if (configNotEmpty.getUsername() != null) {
        log.info(
            "Forward proxy is set to {}://{}:{}@{}:{}",
            configNotEmpty.getType(),
            configNotEmpty.getProxyAddress().getHostName(),
            configNotEmpty.getProxyAddress().getPort(),
            configNotEmpty.getUsername(),
            configNotEmpty.getPassword());
      }
    }
  }

  public void propagateException(final Throwable exception) {
    exceptionListeners.forEach(consumer -> consumer.accept(exception));
  }

  public void addNewExceptionConsumer(final Consumer<Throwable> newConsumer) {
    exceptionListeners.add(newConsumer);
  }

  @Override
  @PreDestroy
  public void close() {
    String tigerProxyName = getName().orElse("");
    log.info("Shutting down Tiger-Proxy {}", tigerProxyName);
    super.close();
    remoteProxyClients.forEach(TigerRemoteProxyClient::close);
    mockServerClient.stop();
    mockServer.stop();
    setSslServerContextBuilderCustomizer();
    KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(null);
  }

  private static class TigerProxyTrustManagerBuildingException extends RuntimeException {

    public TigerProxyTrustManagerBuildingException(final String s, final Exception e) {
      super(s, e);
    }

    public TigerProxyTrustManagerBuildingException(final String s) {
      super(s);
    }
  }
}
