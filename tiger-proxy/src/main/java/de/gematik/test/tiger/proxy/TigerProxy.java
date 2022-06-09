/*
 * Copyright (c) 2022 gematik GmbH
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

import static org.awaitility.Awaitility.await;
import static org.mockserver.model.HttpRequest.request;
import de.gematik.rbellogger.data.RbelElement;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import kong.unirest.Unirest;
import kong.unirest.apache.ApacheClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.impl.client.HttpClients;
import org.apache.tomcat.util.buf.UriUtil;
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
import org.mockserver.socket.tls.KeyAndCertificateFactory;
import org.mockserver.socket.tls.KeyAndCertificateFactoryFactory;
import org.mockserver.socket.tls.NettySslContextFactory;

@Slf4j
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
        ConfigurationProperties.useBouncyCastleForKeyAndCertificateGeneration(true);
        ConfigurationProperties.forwardProxyTLSX509CertificatesTrustManagerType("ANY");
        ConfigurationProperties.maxLogEntries(10);
        if (StringUtils.isNotEmpty(configuration.getProxyLogLevel())) {
            ConfigurationProperties.logLevel(configuration.getProxyLogLevel());
        }
        customizeSslSuitesIfApplicable(configuration);

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

    private void customizeSslSuitesIfApplicable(final TigerProxyConfiguration configuration) {
        if (configuration.getTls().getServerSslSuites() != null) {
            NettySslContextFactory.sslServerContextBuilderCustomizer = builder -> {
                builder.ciphers(configuration.getTls().getServerSslSuites());
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
                    determineServerRootCa().orElse(null),
                    getTigerProxyConfiguration().getTls().getServerIdentity(),
                    getTigerProxyConfiguration().getTls());
                if (isServerInstance) {
                    tlsFactories.add(factory);
                }
                return factory;
            } else {
                return new TigerKeyAndCertificateFactory(mockServerLogger,
                    null, getTigerProxyConfiguration().getTls().getForwardMutualTlsIdentity(),
                    getTigerProxyConfiguration().getTls());
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
            .peek(this::waitForRemoteTigerProxyToBeOnline)
            .map(url -> new TigerRemoteProxyClient(url, TigerProxyConfiguration.builder()
                .downloadInitialTrafficFromEndpoints(
                    getTigerProxyConfiguration().isDownloadInitialTrafficFromEndpoints())
                .connectionTimeoutInSeconds(getTigerProxyConfiguration().getConnectionTimeoutInSeconds())
                .build()))
            .peek(remoteClient -> {
                remoteClient.setRbelLogger(getRbelLogger());
                remoteClient.addRbelMessageListener(this::triggerListener);
            })
            .forEach(remoteProxyClients::add);
    }

    private void addRbelTrafficEndpoint() {
        mockServerClient.when(request()
                .withHeader("Host", "rbel"))
            .respond(HttpResponse.response()
                .withHeader("content-type", "text/html; charset=utf-8")
                .withBody(new RbelHtmlRenderer().doRender(getRbelLogger().getMessageHistory())));
        mockServerClient.when(request()
                .withHeader("Host", null)
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
    public TigerRoute addRoute(final TigerRoute tigerRoute) {
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
            log.info("Our trust managers: " + trustManagers);
            sslContext.init(null, trustManagers, null);

            final HttpClient httpClient = HttpClients.custom()
                .setSSLContext(sslContext)
                .setSSLHostnameVerifier(new DefaultHostnameVerifier())
                .build();

            Unirest.primaryInstance()
                .config()
                .httpClient(config -> ApacheClient.builder(httpClient).apply(config));
            return sslContext;
        } catch (final Exception e) {
            throw new TigerProxyTrustManagerBuildingException("Error while building SSL-Context for Tiger Proxy",
                e);
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

    private class TigerProxyTrustManagerBuildingException extends RuntimeException {

        public TigerProxyTrustManagerBuildingException(final String s, final Exception e) {
            super(s, e);
        }

        public TigerProxyTrustManagerBuildingException(final String s) {
            super(s);
        }
    }
}
