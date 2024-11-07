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

package de.gematik.test.tiger.proxy;

import static de.gematik.test.tiger.mockserver.ExpectationBuilder.when;
import static de.gematik.test.tiger.mockserver.model.HttpRequest.request;
import static de.gematik.test.tiger.proxy.tls.OcspUtils.buildOcspResponse;
import static de.gematik.test.tiger.proxy.tls.TlsCertificateGenerator.generateNewCaCertificate;

import de.gematik.rbellogger.converter.HttpPairingInBinaryChannelConverter;
import de.gematik.rbellogger.util.RbelMessagesSupplier;
import de.gematik.test.tiger.TigerAgent;
import de.gematik.test.tiger.common.config.RbelModificationDescription;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerTlsConfiguration;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.mock.Expectation;
import de.gematik.test.tiger.mockserver.netty.MockServer;
import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration;
import de.gematik.test.tiger.mockserver.socket.tls.KeyAndCertificateFactorySupplier;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import de.gematik.test.tiger.proxy.configuration.ProxyConfigurationConverter;
import de.gematik.test.tiger.proxy.data.TigerConnectionStatus;
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
import java.net.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.net.ssl.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.buf.UriUtil;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;

@EqualsAndHashCode(callSuper = true)
public class TigerProxy extends AbstractTigerProxy implements AutoCloseable, RbelMessagesSupplier {

  private static final String CA_CERT_ALIAS = "caCert";
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
  private TigerPkiIdentity generatedRootCa;

  public TigerProxy(final TigerProxyConfiguration configuration) {
    super(configuration);

    mockServerToRbelConverter = new MockServerToRbelConverter(getRbelLogger().getRbelConverter());
    bootMockServer();

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

  private static void customizeServerBuilderCustomizer(
      MockServerConfiguration mockServerConfiguration, TigerTlsConfiguration tlsConfiguration) {
    mockServerConfiguration.sslServerContextBuilderCustomizer(
        builder -> {
          if (tlsConfiguration.getServerSslSuites() != null) {
            builder.ciphers(tlsConfiguration.getServerSslSuites());
          }
          if (tlsConfiguration.getServerTlsProtocols() != null) {
            builder.protocols(tlsConfiguration.getServerTlsProtocols());
          }
          if (tlsConfiguration.getOcspSignerIdentity() != null) {
            builder.enableOcsp(true);
            mockServerConfiguration.ocspResponseSupplier(
                certificate ->
                    buildOcspResponse(certificate, tlsConfiguration.getOcspSignerIdentity()));
            builder.sslProvider(SslProvider.OPENSSL);
          } else {
            builder.sslProvider(SslProvider.JDK);
            builder.sslContextProvider(new BouncyCastleJsseProvider());
          }

          return builder;
        });
  }

  private static void customizeClientBuilderCustomizer(
      MockServerConfiguration mockServerConfiguration, TigerTlsConfiguration tlsConfiguration) {
    mockServerConfiguration.sslClientContextBuilderCustomizer(
        builder -> {
          if (tlsConfiguration.getClientSslSuites() != null) {
            builder.ciphers(tlsConfiguration.getClientSslSuites());
          }
          builder.sslProvider(SslProvider.JDK);
          return builder;
        });
  }

  private static URL buildUrlSafe(TigerRoute tigerRoute) {
    try {
      return new URL(tigerRoute.getFrom());
    } catch (MalformedURLException e) {
      throw new TigerProxyStartupException("Error while building route", e);
    }
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

    if (getTigerProxyConfiguration().isActivateForwardAllLogging()) {
      mockServer.addRoute(
          when(request().setPath(".*").setForwardProxyRequest(true), Integer.MIN_VALUE, List.of())
              .forward(new ForwardAllCallback(this)));
    }

    addRoutesToTigerProxy();
  }

  private void createNewMockServer() {
    MockServerConfiguration mockServerConfiguration = MockServerConfiguration.configuration();
    mockServerConfiguration.mockServerName(getName().orElse("MockServer"));
    mockServerConfiguration.customKeyAndCertificateFactorySupplier(buildKeyAndCertificateFactory());

    customizeSslIfApplicable(mockServerConfiguration);
    mockServerConfiguration.enableTlsTermination(
        getTigerProxyConfiguration().isActivateTlsTermination());

    final Optional<ProxyConfiguration> proxyConfiguration = ProxyConfigurationConverter.convertForwardProxyConfigurationToMockServerConfiguration(
      getTigerProxyConfiguration());
    outputForwardProxyConfigLogs(proxyConfiguration);
    proxyConfiguration.ifPresent(mockServerConfiguration::proxyConfiguration);

    if (getTigerProxyConfiguration().getDirectReverseProxy() == null) {
      mockServer = new MockServer(mockServerConfiguration, getTigerProxyConfiguration().getPortAsArray());
    } else {
      mockServer = spawnDirectInverseTigerProxy(mockServerConfiguration);
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
      MockServerConfiguration mockServerConfiguration) {
    mockServerConfiguration.binaryProxyListener(new BinaryExchangeHandler(this));
    if (mockServerConfiguration.proxyConfiguration() != null) {
      throw new TigerProxyStartupException(
          "DirectForwardProxy configured with additional forwardProxy: Not possible! (forwardProxy"
              + " is always HTTP!)");
    }
    mockServerConfiguration.directForwarding(InetSocketAddress.createUnresolved(
        getTigerProxyConfiguration().getDirectReverseProxy().getHostname(),
        getTigerProxyConfiguration().getDirectReverseProxy().getPort()));

    MockServer newMockServer =
        new MockServer(
            mockServerConfiguration,
            getTigerProxyConfiguration().getPortAsArray());
    addReverseProxyRouteIfNotPresent();

    getRbelLogger()
        .getRbelConverter()
        .addFirstPostConversionListener(new HttpPairingInBinaryChannelConverter());

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

  private void customizeSslIfApplicable(MockServerConfiguration mockServerConfiguration) {
    final TigerTlsConfiguration tlsConfiguration = getTigerProxyConfiguration().getTls();

    customizeServerBuilderCustomizer(mockServerConfiguration, tlsConfiguration);

    customizeClientBuilderCustomizer(mockServerConfiguration, tlsConfiguration);

    customizeClientBuilderFunction(mockServerConfiguration, tlsConfiguration);

    if (getTigerProxyConfiguration().getTls() != null
        && getTigerProxyConfiguration().getTls().getMasterSecretsFile() != null) {
      TigerAgent.addListener(
          new TigerProxyMasterSecretListener(
              getTigerProxyConfiguration().getTls().getMasterSecretsFile()));
    }
  }

  private void customizeClientBuilderFunction(
      MockServerConfiguration mockServerConfiguration, TigerTlsConfiguration tlsConfiguration) {
    if (tlsConfiguration.getClientSupportedGroups() != null
        && !tlsConfiguration.getClientSupportedGroups().isEmpty()) {
      mockServerConfiguration.clientSslContextBuilderFunction(
          sslContextBuilder -> {
            try {
              System.setProperty(
                  "jdk.tls.namedGroups",
                  String.join(",", tlsConfiguration.getClientSupportedGroups()));
              sslContextBuilder.sslProvider(SslProvider.JDK);
              return sslContextBuilder.build();
            } catch (SSLException e) {
              throw new TigerProxySslException(
                  "Error while building SSL context in Tiger-Proxy " + getName().orElse(""), e);
            }
          });
    }
  }

  private KeyAndCertificateFactorySupplier buildKeyAndCertificateFactory() {
    return (isServerInstance, mockServerConfiguration) -> {
      val tlsConfiguration = Optional.ofNullable(getTigerProxyConfiguration().getTls());
      if (isServerInstance) {
        if (tlsConfiguration.map(TigerTlsConfiguration::getServerIdentity).isPresent()) {
          return new StaticTigerKeyAndCertificateFactory(
              tlsConfiguration.get().getServerIdentity());
        } else if (tlsConfiguration.map(TigerTlsConfiguration::getServerIdentities).isPresent()) {
          return new StaticTigerKeyAndCertificateFactory(
              tlsConfiguration.get().getServerIdentities().stream()
                  .map(TigerPkiIdentity.class::cast)
                  .toList());
        } else {
          final DynamicTigerKeyAndCertificateFactory dynamicTigerKeyAndCertificateFactory =
              new DynamicTigerKeyAndCertificateFactory(
                  getTigerProxyConfiguration(),
                  determineServerRootCa()
                      .orElseThrow(
                          () -> new TigerProxyStartupException("Unrecoverable TLS startup state")),
                  mockServerConfiguration);
          this.tlsFactories.add(dynamicTigerKeyAndCertificateFactory);
          return dynamicTigerKeyAndCertificateFactory;
        }
      } else {
        if (tlsConfiguration.map(TigerTlsConfiguration::getForwardMutualTlsIdentity).isPresent()) {
          return new StaticTigerKeyAndCertificateFactory(
              tlsConfiguration.get().getForwardMutualTlsIdentity());
        } else {
          return new DynamicTigerKeyAndCertificateFactory(
              getTigerProxyConfiguration(),
              new TigerPkiIdentity(
                  "CertificateAuthorityCertificate.pem;CertificateAuthorityPrivateKey.pem;PKCS1"),
              mockServerConfiguration);
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

  public void subscribeToTrafficEndpoints() {
    Optional.of(getTigerProxyConfiguration())
        .map(TigerProxyConfiguration::getTrafficEndpoints)
        .ifPresent(this::subscribeToTrafficEndpoints);
  }

  public void subscribeToTrafficEndpoints(final List<String> trafficEndpointUrls) {
    if (log.isInfoEnabled()) {
      log.info(
          "Subscribing to traffic endpoints for Tiger Proxy '{}'. Found {} endpoints",
          getName().orElse("?"),
          trafficEndpointUrls.size());
    }

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
                        .failOnOfflineTrafficEndpoints(
                            getTigerProxyConfiguration().isFailOnOfflineTrafficEndpoints())
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
    log.info("Adding route {} -> {}", tigerRoute.getFrom(), tigerRoute.getTo());
    final Expectation expectation = buildRouteAndReturnExpectation(tigerRoute);
    expectation.setTigerRoute(tigerRoute);

    final TigerRoute createdTigerRoute = tigerRoute.withId(expectation.getId());
    tigerRouteMap.put(expectation.getId(), createdTigerRoute);

    log.debug("Created route from {} to {}", tigerRoute.getFrom(), tigerRoute.getTo());
    return createdTigerRoute;
  }

  private Expectation buildRouteAndReturnExpectation(final TigerRoute tigerRoute) {
    if (UriUtil.hasScheme(tigerRoute.getFrom())) {
      return buildForwardProxyRoute(tigerRoute);
    } else {
      return buildReverseProxyRoute(tigerRoute);
    }
  }

  private Expectation buildReverseProxyRoute(final TigerRoute tigerRoute) {
    final Expectation expectation =
        when(
                request().setPath(tigerRoute.getFrom() + ".*").setForwardProxyRequest(false),
                0,
                tigerRoute.getHosts())
            .id(tigerRoute.getId())
            .forward(new ReverseProxyCallback(this, tigerRoute));
    mockServer.addRoute(expectation);
    return expectation;
  }

  private Expectation buildForwardProxyRoute(final TigerRoute tigerRoute) {
    final URL url = buildUrlSafe(tigerRoute);
    final Expectation expectation =
        when(
                request()
                    .withHeader("Host", url.getAuthority())
                    .setForwardProxyRequest(true)
                    .setSecure(url.getProtocol().equals("https"))
                    .setPath(extractPath(tigerRoute.getFrom()) + ".*"),
                1_000_000,
                tigerRoute.getHosts())
            .id(tigerRoute.getId())
            .forward(new ForwardProxyCallback(this, tigerRoute));
    mockServer.addRoute(expectation);
    return expectation;
  }

  @SneakyThrows
  private static String extractPath(String url) {
    return new URI(url).getPath();
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
    }
  }

  @Override
  public void removeRoute(final String routeId) {
    if (!mockServer.isRunning()) {
      return;
    }
    mockServer.removeExpectation(routeId);
    final TigerRoute route = tigerRouteMap.remove(routeId);

    log.info(
        "Deleted route {} (id {}). Current # expectations {}",
        route,
        routeId,
        mockServer.retrieveActiveExpectations().size());
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
      storeCertificate(ks, determineServerRootCa().get(), 99);
      val tlsConfiguration = Optional.ofNullable(getTigerProxyConfiguration().getTls());
      if (tlsConfiguration.map(TigerTlsConfiguration::getServerIdentity).isPresent()) {
        storeCertificate(ks, tlsConfiguration.get().getServerIdentity(), 0);
      } else if (tlsConfiguration.map(TigerTlsConfiguration::getServerIdentities).isPresent()) {
        val certCounter = new AtomicInteger(0);
        tlsConfiguration.map(TigerTlsConfiguration::getServerIdentities).stream()
            .flatMap(Collection::stream)
            .forEach(id -> storeCertificate(ks, id, certCounter.getAndIncrement()));
      }
      if (tlsConfiguration.map(TigerTlsConfiguration::getOcspSignerIdentity).isPresent()) {
        ks.setCertificateEntry(
            "ocspSignerCert", tlsConfiguration.get().getOcspSignerIdentity().getCertificate());
      }
      return ks;
    } catch (final Exception e) {
      throw new TigerProxyTrustManagerBuildingException(
          "Error while building SSL-Context for Tiger Proxy", e);
    }
  }

  @SneakyThrows
  private void storeCertificate(KeyStore ks, TigerPkiIdentity identity, int counter) {
    ks.setCertificateEntry(CA_CERT_ALIAS + "_" + counter, identity.getCertificate());
    int chainCertCtr = 0;
    for (final X509Certificate chainCert : identity.getCertificateChain()) {
      ks.setCertificateEntry("chainCert_" + counter + "_" + chainCertCtr++, chainCert);
    }
  }

  public SSLContext buildSslContext() {
    try {
      TrustManagerFactory tmf =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(buildTruststore());
      TrustManager[] trustManagers = tmf.getTrustManagers();

      SSLContext sslContext = SSLContext.getInstance("TLS", new BouncyCastleJsseProvider());
      sslContext.init(null, trustManagers, null);
      return sslContext;
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
        log.atInfo()
            .addArgument(() -> configNotEmpty.getType().toString().toLowerCase())
            .addArgument(() -> configNotEmpty.getProxyAddress().getAddress())
            .addArgument(() -> configNotEmpty.getProxyAddress().getPort())
            .log("Forward proxy is set to {}://{}:{}");
      } else if (configNotEmpty.getUsername() != null) {
        log.atInfo()
            .addArgument(() -> configNotEmpty.getType().toString().toLowerCase())
            .addArgument(() -> configNotEmpty.getProxyAddress().getAddress())
            .addArgument(() -> configNotEmpty.getProxyAddress().getPort())
            .addArgument(configNotEmpty::getUsername)
            .addArgument(configNotEmpty::getPassword)
            .log("Forward proxy is set to {}://{}:{}@{}:{}");
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
    mockServer.stop();
  }

  public Map<SocketAddress, TigerConnectionStatus> getOpenConnections() {
    return getOpenConnections(TigerConnectionStatus.OPEN_TCP);
  }

  public Map<SocketAddress, TigerConnectionStatus> getOpenConnections(
      TigerConnectionStatus status) {
    return mockServer.getOpenConnections().entrySet().stream()
        .filter(entry -> entry.getValue().getValue() >= status.getValue())
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public void waitForAllCurrentMessagesToBeParsed() {
    if (!getRbelLogger().getMessageHistory().isEmpty()) {
      getRbelLogger().getRbelConverter().waitForAllCurrentMessagesToBeParsed();
    }
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
