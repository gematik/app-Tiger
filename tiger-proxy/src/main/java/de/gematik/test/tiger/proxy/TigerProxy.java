/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.modifier.RbelModificationDescription;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyType;
import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.config.tigerProxy.TigerTlsConfiguration;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.exception.TigerProxyStartupException;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyConfigurationException;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyRouteConflictException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import org.mockserver.proxyconfiguration.ProxyConfiguration.Type;
import org.mockserver.socket.tls.KeyAndCertificateFactory;
import org.mockserver.socket.tls.KeyAndCertificateFactoryFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;

@Slf4j
public class TigerProxy extends AbstractTigerProxy {

    private final List<TigerKeyAndCertificateFactory> tlsFactories = new ArrayList<>();
    private final MockServer mockServer;
    private final MockServerClient mockServerClient;
    @Getter
    private final MockServerToRbelConverter mockServerToRbelConverter;
    private final Map<String, TigerRoute> tigerRouteMap = new HashMap<>();

    public TigerProxy(TigerProxyConfiguration configuration) {
        super(configuration);

        KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(buildKeyAndCertificateFactory());

        mockServerToRbelConverter = new MockServerToRbelConverter(getRbelLogger().getRbelConverter());
        ConfigurationProperties.useBouncyCastleForKeyAndCertificateGeneration(true);
        ConfigurationProperties.forwardProxyTLSX509CertificatesTrustManagerType("ANY");
        ConfigurationProperties.maxLogEntries(10);
        if (StringUtils.isNotEmpty(configuration.getProxyLogLevel())) {
            ConfigurationProperties.logLevel(configuration.getProxyLogLevel());
        }

        mockServer = convertProxyConfiguration()
            .map(proxyConfiguration -> new MockServer(proxyConfiguration, configuration.getPortAsArray()))
            .orElseGet(() -> new MockServer(configuration.getPortAsArray()));
        log.info("Proxy started on port " + mockServer.getLocalPort());

        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort()) {

        };
        if (configuration.getProxyRoutes() != null) {
            for (TigerRoute tigerRoute : configuration.getProxyRoutes()) {
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
            for (RbelModificationDescription modification : configuration.getModifications()) {
                if (modification.getName() == null) {
                    modification.setName("TigerModification #" + counter++);
                    getRbelLogger().getRbelModifier().addModification(modification);
                } else {
                    getRbelLogger().getRbelModifier().addModification(modification);
                }
            }
        }

        if (configuration.isActivateForwardAllLogging()) {
            mockServerClient.when(request()
                    .withPath(".*"), Times.unlimited(), TimeToLive.unlimited(), Integer.MIN_VALUE)
                .forward(req -> forwardOverriddenRequest(
                        req.withSocketAddress(
                            req.isSecure(),
                            req.socketAddressFromHostHeader().getHostName(),
                            req.socketAddressFromHostHeader().getPort()
                        )).getHttpRequest(),
                    (req, resp) -> {
                        try {
                            triggerListener(mockServerToRbelConverter.convertRequest(req,
                                req.getSocketAddress().getScheme() + "://" + req.getSocketAddress().getHost() + ":"
                                    + req.getSocketAddress().getPort()));
                            triggerListener(mockServerToRbelConverter.convertResponse(resp,
                                req.getSocketAddress().getScheme() + "://" + req.getSocketAddress().getHost() + ":"
                                    + req.getSocketAddress().getPort()));
                            manageRbelBufferSize();
                        } catch (Exception e) {
                            log.error("RBel FAILED!", e);
                        }
                        return resp;
                    });
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

    public void subscribeToTrafficEndpoints(TigerProxyConfiguration configuration) {
        Optional.of(configuration)
            .filter(Objects::nonNull)
            .map(TigerProxyConfiguration::getTrafficEndpoints)
            .ifPresent(this::subscribeToTrafficEndpoints);
    }

    public void subscribeToTrafficEndpoints(List<String> trafficEndpointUrls) {
        Optional.of(trafficEndpointUrls)
            .filter(Objects::nonNull)
            .stream()
            .flatMap(List::stream)
            .map(url -> new TigerRemoteProxyClient(url, new TigerProxyConfiguration()))
            .forEach(remoteClient -> {
                remoteClient.setRbelLogger(getRbelLogger());
                remoteClient.addRbelMessageListener(this::triggerListener);
            });
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

    public ProxyConfiguration.Type toMockServerType(TigerProxyType type) {
        if (type == TigerProxyType.HTTP) {
            return ProxyConfiguration.Type.HTTP;
        } else {
            return ProxyConfiguration.Type.HTTPS;
        }
    }

    private Optional<ProxyConfiguration> convertProxyConfiguration() {
        Optional<ProxyConfiguration> cfg = Optional.empty();
        try {
            if (getTigerProxyConfiguration().getForwardToProxy() == null
                || StringUtils.isEmpty(getTigerProxyConfiguration().getForwardToProxy().getHostname())) {
                return cfg;
            }
            if (getTigerProxyConfiguration().getForwardToProxy().getHostname() != null &&
                getTigerProxyConfiguration().getForwardToProxy().getHostname().equals("$SYSTEM")) {
                if (System.getProperty("http.proxyHost") != null) {
                    cfg = Optional.of(ProxyConfiguration.proxyConfiguration(Type.HTTP,
                        System.getProperty("http.proxyHost") + ":" + System.getProperty("http.proxyPort")));
                } else if (System.getenv("http_proxy") != null) {
                    cfg = Optional.of(ProxyConfiguration.proxyConfiguration(Type.HTTP,
                        System.getenv("http_proxy").split("://")[1]));
                }
            } else {
                cfg = Optional.of(ProxyConfiguration.proxyConfiguration(
                    Optional.ofNullable(getTigerProxyConfiguration().getForwardToProxy().getType())
                        .map(this::toMockServerType)
                        .orElse(Type.HTTPS),
                    getTigerProxyConfiguration().getForwardToProxy().getHostname()
                        + ":"
                        + getTigerProxyConfiguration().getForwardToProxy().getPort()));
            }
            return cfg;
        } finally {
            if (cfg.isEmpty()) {
                log.info("Tigerproxy has NO forward proxy configured!");
            } else {
                log.info("Forward proxy is set to " +
                    cfg.get().getType() + "://" + cfg.get().getProxyAddress().getHostName() + ":" + cfg.get()
                    .getProxyAddress().getPort());
            }
        }
    }

    @Override
    public String getBaseUrl() {
        return "http://localhost:" + mockServer.getLocalPort();
    }

    @Override
    public int getPort() {
        return mockServer.getLocalPort();
    }

    @Override
    public List<TigerRoute> getRoutes() {
        return tigerRouteMap.entrySet().stream()
            .map(Entry::getValue)
            .collect(Collectors.toList());
    }

    @Override
    public TigerRoute addRoute(final TigerRoute tigerRoute) {
        tigerRouteMap.values().stream()
            .filter(existingRoute -> uriEquals(existingRoute.getFrom(), tigerRoute.getFrom()))
            .findAny()
            .ifPresent(existingRoute -> {
                throw new TigerProxyRouteConflictException(existingRoute);
            });

        log.info("adding route {} -> {}", tigerRoute.getFrom(), tigerRoute.getTo());
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

    private boolean uriEquals(String value1, String value2) {
        try {
            return new URI(value1).equals(new URI(value2));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private Expectation[] buildRouteAndReturnExpectation(TigerRoute tigerRoute) {
        if (UriUtil.hasScheme(tigerRoute.getFrom())) {
            return buildForwardProxyRoute(tigerRoute);
        } else {
            return buildReverseProxyRoute(tigerRoute);
        }
    }

    private Expectation[] buildReverseProxyRoute(TigerRoute tigerRoute) {
        return mockServerClient.when(request()
                .withPath(tigerRoute.getFrom() + ".*"))
            .forward(new ReverseProxyCallback(this, tigerRoute));
    }

    private Expectation[] buildForwardProxyRoute(TigerRoute tigerRoute) {
        return mockServerClient.when(request()
                .withHeader("Host", tigerRoute.getFrom().split("://")[1])
                .withSecure(tigerRoute.getFrom().startsWith("https://")))
            .forward(new ForwardProxyCallback(this, tigerRoute));
    }

    public void addAlternativeName(String host) {
        List<String> newAlternativeNames = new ArrayList<>();
        if (getTigerProxyConfiguration().getTls() != null
            && getTigerProxyConfiguration().getTls().getAlternativeNames() != null) {
            newAlternativeNames.addAll(getTigerProxyConfiguration().getTls().getAlternativeNames());
        }
        newAlternativeNames.add(host);
        getTigerProxyConfiguration().getTls().setAlternativeNames(newAlternativeNames);

        for (TigerKeyAndCertificateFactory tlsFactory : tlsFactories) {
            tlsFactory.addAlternativeName(host);
            tlsFactory.resetEeCertificate();
        }
    }

    public void manageRbelBufferSize() {
        while (rbelBufferIsExceedingMaxSize()) {
            log.info("Exceeded buffer size, dropping oldest message in history");
            getRbelLogger().getMessageHistory().remove(0);
        }
    }

    private boolean rbelBufferIsExceedingMaxSize() {
        final long bufferSize = getRbelLogger().getMessageHistory().stream()
            .map(RbelElement::getRawContent)
            .mapToLong(ar -> ar.length)
            .sum();
        final boolean exceedingLimit = bufferSize > (getTigerProxyConfiguration().getRbelBufferSizeInMb() * 1024 * 1024);
        if (exceedingLimit) {
            log.info("Buffersize is {} Mb which exceeds the limit of {} Mb",
                bufferSize / (1024 ^ 2), getTigerProxyConfiguration().getRbelBufferSizeInMb());
        }
        return exceedingLimit;
    }

    @Override
    public void removeRoute(String routeId) {
        mockServerClient.clear(new ExpectationId().withId(routeId));
        final TigerRoute route = tigerRouteMap.remove(routeId);

        log.info("Deleted route {}. Current # expectations {}",
            route,
            mockServerClient.retrieveActiveExpectations(request()).length);
    }

    public KeyStore buildTruststore() {
        try {
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null);
            final TigerPkiIdentity serverIdentity = determineServerRootCa()
                .or(() -> Optional.ofNullable(getTigerProxyConfiguration().getTls())
                    .map(TigerTlsConfiguration::getServerIdentity)
                    .filter(Objects::nonNull))
                .orElseThrow(() -> new TigerProxyTrustManagerBuildingException(
                    "Unrecoverable state: Server-Identity null and Server-CA empty"));

            ks.setCertificateEntry("caCert", serverIdentity.getCertificate());
            int chainCertCtr = 0;
            for (X509Certificate chainCert : serverIdentity.getCertificateChain()) {
                ks.setCertificateEntry("chainCert" + chainCertCtr++, chainCert);
            }
            return ks;
        } catch (Exception e) {
            throw new TigerProxyTrustManagerBuildingException("Error while building SSL-Context for tiger-proxy", e);
        }
    }

    public SSLContext buildSslContext() {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

            tmf.init(buildTruststore());

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);
            return sslContext;
        } catch (Exception e) {
            throw new TigerProxyTrustManagerBuildingException("Error while building SSL-Context for tiger-proxy", e);
        }
    }

    private class TigerProxyTrustManagerBuildingException extends RuntimeException {

        public TigerProxyTrustManagerBuildingException(String s, Exception e) {
            super(s, e);
        }

        public TigerProxyTrustManagerBuildingException(String s) {
            super(s);
        }
    }

    public void shutdown() {
        mockServerClient.stop();
        mockServer.stop();
    }
}
