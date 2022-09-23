/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static org.mockserver.model.HttpRequest.request;
import de.gematik.rbellogger.modifier.RbelModificationDescription;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerTlsConfiguration;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyConfigurationException;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyRouteConflictException;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyStartupException;
import io.netty.handler.ssl.SslContextBuilder;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.net.ssl.*;
import kong.unirest.Unirest;
import kong.unirest.apache.ApacheClient;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.impl.client.HttpClients;
import org.apache.tomcat.util.buf.UriUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.ExpectationId;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.MockServer;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.socket.tls.ForwardProxyTLSX509CertificatesTrustManager;
import org.mockserver.socket.tls.KeyAndCertificateFactory;
import org.mockserver.socket.tls.KeyAndCertificateFactoryFactory;
import org.mockserver.socket.tls.NettySslContextFactory;

public class TigerProxy extends AbstractTigerProxy implements AutoCloseable {

    private final List<TigerKeyAndCertificateFactory> tlsFactories = new ArrayList<>();
    private final List<Consumer<Throwable>> exceptionListeners = new ArrayList<>();
    private final MockServer mockServer;
    private final MockServerClient mockServerClient;
    @Getter
    private final MockServerToRbelConverter mockServerToRbelConverter;
    private final Map<String, TigerRoute> tigerRouteMap = new HashMap<>();
    private final List<TigerRemoteProxyClient> remoteProxyClients = new ArrayList<>();

    public TigerProxy(final TigerProxyConfiguration configuration) {
        super(configuration);

        KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(buildKeyAndCertificateFactory());

        mockServerToRbelConverter = new MockServerToRbelConverter(getRbelLogger().getRbelConverter());
        ConfigurationProperties.forwardProxyTLSX509CertificatesTrustManagerType(ForwardProxyTLSX509CertificatesTrustManager.ANY);
        ConfigurationProperties.maxLogEntries(0);
        if (StringUtils.isNotEmpty(configuration.getProxyLogLevel())) {
            ConfigurationProperties.logLevel(configuration.getProxyLogLevel());
        }

        customizeSslSuitesIfApplicable();

        final Optional<ProxyConfiguration> forwardProxyConfig = configuration.convertForwardProxyConfigurationToMockServerConfiguration();
        outputForwardProxyConfigLogs(forwardProxyConfig);

        mockServer = forwardProxyConfig
            .map(proxyConfiguration -> new MockServer(proxyConfiguration, configuration.getPortAsArray()))
            .orElseGet(() -> new MockServer(configuration.getPortAsArray()));
        log.info("Proxy started on port " + mockServer.getLocalPort());

        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort());
        if (configuration.getProxyRoutes() != null) {
            for (final TigerRoute tigerRoute : configuration.getProxyRoutes()) {
                addRoute(tigerRoute);
            }
        }
        if (configuration.isActivateRbelEndpoint()) {
            addRbelTrafficEndpoint();
        }

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

        if (configuration.isActivateForwardAllLogging()) {
            mockServerClient.when(request()
                    .withPath(".*"), Times.unlimited(), TimeToLive.unlimited(), Integer.MIN_VALUE)
                .forward(new ForwardAllCallback(this));
        }
    }

    private void customizeSslSuitesIfApplicable() {
        if (getTigerProxyConfiguration().getTls().getServerSslSuites() != null) {
            NettySslContextFactory.sslServerContextBuilderCustomizer = builder -> {
                builder.ciphers(getTigerProxyConfiguration().getTls().getServerSslSuites());
                return builder;
            };
        }
        if (getTigerProxyConfiguration().getTls().getClientSslSuites() != null) {
            NettySslContextFactory.sslClientContextBuilderCustomizer = builder -> {
                builder.ciphers(getTigerProxyConfiguration().getTls().getClientSslSuites());
                return builder;
            };
        }
    }

    private BiFunction<MockServerLogger, Boolean, KeyAndCertificateFactory> buildKeyAndCertificateFactory() {
        if (getTigerProxyConfiguration().getTls().getServerIdentity() != null
            && !getTigerProxyConfiguration().getTls().getServerIdentity().hasValidChainWithRootCa()) {
            throw new TigerProxyStartupException("Configured server-identity has no valid chain!");
        }

        return (mockServerLogger, isServerInstance) -> {
            if (isServerInstance
                || getTigerProxyConfiguration().getTls() == null
                || getTigerProxyConfiguration().getTls().getForwardMutualTlsIdentity() == null) {
                final TigerKeyAndCertificateFactory factory = new TigerKeyAndCertificateFactory(mockServerLogger,
                    getTigerProxyConfiguration(),
                    determineServerRootCa().orElse(null));
                if (isServerInstance) {
                    tlsFactories.add(factory);
                }
                return factory;
            } else {
                return new TigerKeyAndCertificateFactory(mockServerLogger,
                    getTigerProxyConfiguration(), null);
            }
        };
    }

    private Optional<TigerPkiIdentity> determineServerRootCa() {
        if (getTigerProxyConfiguration().getTls().getServerRootCa() != null) {
            return Optional.ofNullable(getTigerProxyConfiguration().getTls().getServerRootCa());
        } else {
            if (getTigerProxyConfiguration().getTls().getServerIdentity() != null) {
                return Optional.empty();
            } else {
                return Optional.of(new TigerPkiIdentity(
                    "CertificateAuthorityCertificate.pem;" +
                        "PKCS8CertificateAuthorityPrivateKey.pem;" +
                        "PKCS8"));
            }
        }
    }

    public void subscribeToTrafficEndpoints(final TigerProxyConfiguration configuration) {
        Optional.of(configuration)
            .filter(Objects::nonNull)
            .map(TigerProxyConfiguration::getTrafficEndpoints)
            .ifPresent(this::subscribeToTrafficEndpoints);
    }

    public void subscribeToTrafficEndpoints(final List<String> trafficEndpointUrls) {
        Optional.of(trafficEndpointUrls)
            .filter(Objects::nonNull)
            .stream()
            .flatMap(List::stream)
            .parallel()
            .map(url -> new TigerRemoteProxyClient(url, TigerProxyConfiguration.builder()
                .downloadInitialTrafficFromEndpoints(
                    getTigerProxyConfiguration().isDownloadInitialTrafficFromEndpoints())
                .trafficEndpointFilterString(getTigerProxyConfiguration().getTrafficEndpointFilterString())
                .name(getTigerProxyConfiguration().getName())
                .connectionTimeoutInSeconds(getTigerProxyConfiguration().getConnectionTimeoutInSeconds())
                .build(), this))
            .forEach(remoteProxyClients::add);
    }

    private void addRbelTrafficEndpoint() {
        mockServerClient.when(request()
                .withHeader("Host", "rbel"))
            .respond(HttpResponse.response()
                .withHeader("content-type", "text/html; charset=utf-8")
                .withBody(new RbelHtmlRenderer().doRender(getRbelLogger().getMessageHistory())));
        mockServerClient.when(request()
                .withHeader("Host", ".*")
                .withPath("/rbel"))
            .respond(httpRequest ->
                HttpResponse.response()
                    .withHeader("content-type", "text/html; charset=utf-8")
                    .withBody(new RbelHtmlRenderer().doRender(getRbelLogger().getMessageHistory())));
    }

    @Override
    public String getBaseUrl() {
        return "http://localhost:" + mockServer.getLocalPort();
    }

    @Override
    public int getProxyPort() {
        return mockServer.getLocalPort();
    }

    @Override
    public List<TigerRoute> getRoutes() {
        return tigerRouteMap.values().stream().collect(Collectors.toUnmodifiableList());
    }

    @Override
    public RbelModificationDescription addModificaton(final RbelModificationDescription modification) {
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
        tigerRouteMap.values().stream()
            .filter(existingRoute ->
                uriTwoIsBelowUriOne(existingRoute.getFrom(), tigerRoute.getFrom())
                    || uriTwoIsBelowUriOne(tigerRoute.getFrom(), existingRoute.getFrom()))
            .findAny()
            .ifPresent(existingRoute -> {
                throw new TigerProxyRouteConflictException(existingRoute);
            });

        log.info("Adding route {} -> {}", tigerRoute.getFrom(), tigerRoute.getTo());
        final Expectation[] expectations = buildRouteAndReturnExpectation(tigerRoute);
        if (expectations.length > 1) {
            log.warn("Unexpected number of expectations created! Got {}, expected 1", expectations.length);
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

    private boolean uriTwoIsBelowUriOne(final String value1, final String value2) {
        try {
            final URI uri1 = new URI(value1);
            final URI uri2WithUri1Scheme = new URIBuilder(value2)
                .setScheme(uri1.getScheme()).build();
            return !new URI(value1)
                .relativize(uri2WithUri1Scheme).equals(uri2WithUri1Scheme);
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
        return mockServerClient.when(request()
                .withPath(tigerRoute.getFrom() + ".*"))
            .forward(new ReverseProxyCallback(this, tigerRoute));
    }

    private Expectation[] buildForwardProxyRoute(final TigerRoute tigerRoute) {
        return mockServerClient.when(request()
                .withHeader("Host", tigerRoute.getFrom().split("://")[1])
                .withSecure(tigerRoute.getFrom().startsWith("https://")))
            .forward(new ForwardProxyCallback(this, tigerRoute));
    }

    public void addAlternativeName(final String host) {
        final List<String> newAlternativeNames = new ArrayList<>();
        if (getTigerProxyConfiguration().getTls() != null
            && getTigerProxyConfiguration().getTls().getAlternativeNames() != null) {
            newAlternativeNames.addAll(getTigerProxyConfiguration().getTls().getAlternativeNames());
        }
        newAlternativeNames.add(host);
        getTigerProxyConfiguration().getTls().setAlternativeNames(newAlternativeNames);

        for (final TigerKeyAndCertificateFactory tlsFactory : tlsFactories) {
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

        log.info("Deleted route {}. Current # expectations {}",
            route,
            mockServerClient.retrieveActiveExpectations(request()).length);
    }

    public SSLContext getConfiguredTigerProxySslContext() {
        try {
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{buildTrustManagerForTigerProxy()}, null);

            SSLContext.setDefault(sslContext);
            return sslContext;
        } catch (final Exception e) {
            throw new TigerProxyTrustManagerBuildingException("Error while configuring SSL Context for Tiger Proxy",
                e);
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
                public void checkServerTrusted(final X509Certificate[] chain,
                    final String authType) throws CertificateException {
                    try {
                        customTrustManager.checkServerTrusted(chain, authType);
                    } catch (final CertificateException e) {
                        defaultTrustManager.checkServerTrusted(chain, authType);
                    }
                }

                @Override
                public void checkClientTrusted(final X509Certificate[] chain,
                    final String authType) throws CertificateException {
                    defaultTrustManager.checkClientTrusted(chain, authType);
                }
            };
        } catch (final Exception e) {
            throw new TigerProxyTrustManagerBuildingException("Error while building TrustManager for Tiger Proxy",
                e);
        }
    }

    private X509TrustManager extractTrustManager(final KeyStore keystore)
        throws NoSuchAlgorithmException, KeyStoreException {
        final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keystore);

        return (X509TrustManager) Arrays.stream(trustManagerFactory.getTrustManagers())
            .filter(X509TrustManager.class::isInstance)
            .findAny()
            .orElseThrow(() -> new TigerProxyTrustManagerBuildingException(
                "Error while configuring TrustManager for Tiger Proxy"));
    }

    public KeyStore buildTruststore() {
        try {
            final KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null);
            final TigerPkiIdentity serverIdentity = determineServerRootCa()
                .or(() -> Optional.ofNullable(getTigerProxyConfiguration().getTls())
                    .map(TigerTlsConfiguration::getServerIdentity)
                    .filter(Objects::nonNull))
                .orElseThrow(() -> new TigerProxyTrustManagerBuildingException(
                    "Unrecoverable state: Server-Identity null and Server-CA empty"));

            ks.setCertificateEntry("caCert", serverIdentity.getCertificate());
            int chainCertCtr = 0;
            for (final X509Certificate chainCert : serverIdentity.getCertificateChain()) {
                ks.setCertificateEntry("chainCert" + chainCertCtr++, chainCert);
            }
            return ks;
        } catch (final Exception e) {
            throw new TigerProxyTrustManagerBuildingException("Error while building SSL-Context for Tiger Proxy",
                e);
        }
    }

    public SSLContext buildSslContext() {
        try {
            final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

            tmf.init(buildTruststore());

            SSLContext sslContext = SSLContext.getInstance("TLS");
            TrustManager[] trustManagers = tmf.getTrustManagers();
            sslContext.init(null, trustManagers, null);

            final HttpClient httpClient = HttpClients.custom()
                .setSSLContext(sslContext)
                .setSSLHostnameVerifier(new DefaultHostnameVerifier())
                .build();

            Unirest.primaryInstance()
                .config()
                .httpClient(config -> ApacheClient.builder(httpClient).apply(config));
            return sslContext;
        } catch (final RuntimeException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new TigerProxyTrustManagerBuildingException("Error while building SSL-Context for Tiger Proxy", e);
        }
    }

    private void outputForwardProxyConfigLogs(final Optional<ProxyConfiguration> forwardProxyConfig) {
        if (forwardProxyConfig.isEmpty()) {
            log.info("Tigerproxy has NO forward proxy configured!");
        } else {
            final ProxyConfiguration configNotEmpty = forwardProxyConfig.get();
            if (configNotEmpty.getUsername() == null) {
                log.info("Forward proxy is set to " +
                    configNotEmpty.getType() + "://"
                    + configNotEmpty.getProxyAddress().getHostName() + ":"
                    + configNotEmpty.getProxyAddress().getPort());
            } else if (configNotEmpty.getUsername() != null) {
                log.info("Forward proxy is set to " +
                    configNotEmpty.getType() + "://"
                    + configNotEmpty.getProxyAddress().getHostName() + ":"
                    + configNotEmpty.getProxyAddress().getPort() + "@"
                    + configNotEmpty.getUsername() + ":"
                    + configNotEmpty.getPassword());
            }
        }
    }

    public void propagateException(final Throwable exception) {
        exceptionListeners
            .forEach(consumer -> consumer.accept(exception));
    }

    public void addNewExceptionConsumer(final Consumer<Throwable> newConsumer) {
        exceptionListeners.add(newConsumer);
    }

    public void shutdown() {
        remoteProxyClients
            .forEach(TigerRemoteProxyClient::close);
        mockServerClient.stop();
        mockServer.stop();
    }

    @Override
    public void close() throws Exception {
        shutdown();
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
