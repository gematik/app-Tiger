/*
 * Copyright (c) 2021 gematik GmbH
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

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyType;
import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.exception.TigerProxyStartupException;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyConfigurationException;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyRouteConflictException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.buf.UriUtil;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import org.mockserver.model.ExpectationId;
import org.mockserver.model.HttpRequest;
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

import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;

@Slf4j
public class TigerProxy extends AbstractTigerProxy {

    private MockServer mockServer;
    private MockServerClient mockServerClient;
    private MockServerToRbelConverter mockServerToRbelConverter;
    private Map<String, TigerRoute> tigerRouteMap = new HashMap<>();

    public TigerProxy(TigerProxyConfiguration configuration) {
        super(configuration);

        KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(buildKeyAndCertificateFactory(configuration));

        mockServerToRbelConverter = new MockServerToRbelConverter(getRbelLogger().getRbelConverter());
        ConfigurationProperties.useBouncyCastleForKeyAndCertificateGeneration(true);
        ConfigurationProperties.forwardProxyTLSX509CertificatesTrustManagerType("ANY");
        ConfigurationProperties.maxLogEntries(10);
        if (StringUtils.isNotEmpty(configuration.getProxyLogLevel())) {
            ConfigurationProperties.logLevel(configuration.getProxyLogLevel());
        }

        mockServer = convertProxyConfiguration(configuration)
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

    private BiFunction<MockServerLogger, Boolean, KeyAndCertificateFactory>
    buildKeyAndCertificateFactory(TigerProxyConfiguration configuration) {
        if (getTigerProxyConfiguration().getServerIdentity() != null
            && !getTigerProxyConfiguration().getServerIdentity().hasValidChainWithRootCa()) {
            throw new TigerProxyStartupException("Configured server-identity has no valid chain!");
        }

        return (mockServerLogger, isServerInstance) -> {
            if (isServerInstance
                || configuration.getForwardMutualTlsIdentity() == null) {
                return new TigerKeyAndCertificateFactory(mockServerLogger,
                    determineServerRootCa().orElse(null),
                    getTigerProxyConfiguration().getServerIdentity());
            } else {
                return new TigerKeyAndCertificateFactory(mockServerLogger,
                    null, configuration.getForwardMutualTlsIdentity());
            }
        };
    }

    private Optional<TigerPkiIdentity> determineServerRootCa() {
        if (getTigerProxyConfiguration().getServerRootCa() != null) {
            return Optional.ofNullable(getTigerProxyConfiguration().getServerRootCa());
        } else {
            if (getTigerProxyConfiguration().getServerIdentity() != null) {
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

    private Optional<ProxyConfiguration> convertProxyConfiguration(TigerProxyConfiguration configuration) {
        Optional<ProxyConfiguration> cfg = Optional.empty();
        try {
            if (configuration.getForwardToProxy() == null
                || StringUtils.isEmpty(configuration.getForwardToProxy().getHostname())) {
                return cfg;
            }
            if (configuration.getForwardToProxy().getHostname() != null &&
                configuration.getForwardToProxy().getHostname().equals("$SYSTEM")) {
                if (System.getProperty("http.proxyHost") != null) {
                    cfg = Optional.of(ProxyConfiguration.proxyConfiguration(Type.HTTP,
                        System.getProperty("http.proxyHost") + ":" + System.getProperty("http.proxyPort")));
                } else if (System.getenv("http_proxy") != null) {
                    cfg = Optional.of(ProxyConfiguration.proxyConfiguration(Type.HTTP,
                        System.getenv("http_proxy").split("://")[1]));
                }
            } else {
                cfg = Optional.of(ProxyConfiguration.proxyConfiguration(
                    Optional.ofNullable(configuration.getForwardToProxy().getType())
                        .map(this::toMockServerType)
                        .orElse(Type.HTTPS),
                    configuration.getForwardToProxy().getHostname()
                        + ":"
                        + configuration.getForwardToProxy().getPort()));
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
            .forward(
                new RoutedForwardRequestExpectationForwardAndResponseCallback(tigerRoute)
            );
    }

    @SneakyThrows(URISyntaxException.class)
    private Expectation[] buildForwardProxyRoute(TigerRoute tigerRoute) {
        final URI targetUri = new URI(tigerRoute.getTo());
        final int port;
        if (targetUri.getPort() < 0) {
            port = tigerRoute.getTo().startsWith("https://") ? 443 : 80;
        } else {
            port = targetUri.getPort();
        }
        return mockServerClient.when(request()
                .withHeader("Host", tigerRoute.getFrom().split("://")[1])
                .withSecure(tigerRoute.getFrom().startsWith("https://")))
            .forward(
                req -> {
                    req.replaceHeader(header("Host", targetUri.getHost() + ":" + port));
                    if (tigerRoute.getBasicAuth() != null) {
                        req.replaceHeader(
                            header(
                                "Authorization",
                                tigerRoute.getBasicAuth().toAuthorizationHeaderValue()));
                    }
                    final String path = req.getPath().equals("/") ?
                        targetUri.getPath()
                        : targetUri.getPath() + req.getPath();
                    return forwardOverriddenRequest(req)
                        .getHttpRequest()
                        .withPath(path)
                        .withSecure(tigerRoute.getTo().startsWith("https://"));
                },
                buildExpectationCallback(tigerRoute, tigerRoute.getFrom())
            );
    }

    private ExpectationForwardAndResponseCallback buildExpectationCallback(TigerRoute tigerRoute,
                                                                           String protocolAndHost) {
        return (req, resp) -> {
            if (!tigerRoute.isDisableRbelLogging()) {
                try {
                    triggerListener(mockServerToRbelConverter.convertRequest(req, protocolAndHost));
                    triggerListener(mockServerToRbelConverter.convertResponse(resp, protocolAndHost));
                    manageRbelBufferSize();
                } catch (Exception e) {
                    log.error("RBel FAILED!", e);
                }
            }
            return resp;
        };
    }

    private void manageRbelBufferSize() {
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
                .or(() -> Optional.ofNullable(getTigerProxyConfiguration().getServerIdentity()))
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

    private class RoutedForwardRequestExpectationForwardAndResponseCallback implements ExpectationForwardAndResponseCallback {

        private final TigerRoute route;
        private final URI targetUri;
        private final int port;

        @SneakyThrows(URISyntaxException.class)
        RoutedForwardRequestExpectationForwardAndResponseCallback(TigerRoute route) {
            this.route = route;
            this.targetUri = new URI(route.getTo());
            if (targetUri.getPort() < 0) {
                port = route.getTo().startsWith("https://") ? 443 : 80;
            } else {
                port = targetUri.getPort();
            }

        }

        @Override
        public HttpRequest handle(HttpRequest httpRequest) {
            final HttpRequest request = httpRequest.withSocketAddress(
                    route.getTo().startsWith("https://"),
                    targetUri.getHost(),
                    port
                ).withSecure(route.getTo().startsWith("https://"))
                .removeHeader("Host")
                .withPath(patchPath(httpRequest.getPath().getValue()));

            request.withHeader("Host", targetUri.getHost() + ":" + port);
            if (route.getBasicAuth() != null) {
                request.withHeader("Authorization", route.getBasicAuth().toAuthorizationHeaderValue());
            }

            return request;
        }

        private String patchPath(String requestPath) {
            String patchedUrl = requestPath.replaceFirst(targetUri.toString(), "");
            if (!route.getFrom().equals("/")) {
                patchedUrl = patchedUrl.substring(route.getFrom().length());
            }
            if (patchedUrl.startsWith("/")) {
                return targetUri.getPath() + patchedUrl;
            } else {
                return targetUri.getPath() + "/" + patchedUrl;
            }
        }

        @Override
        public HttpResponse handle(HttpRequest httpRequest, HttpResponse httpResponse) {
            if (!route.isDisableRbelLogging()) {
                try {
                    triggerListener(mockServerToRbelConverter.convertRequest(httpRequest, route.getFrom()));
                    triggerListener(mockServerToRbelConverter.convertResponse(httpResponse, route.getFrom()));
                } catch (Exception e) {
                    log.error("RBel FAILED!", e);
                }
            }
            String newBody = httpResponse.getBodyAsString()
                .replaceAll("http[s|]*\\:\\/\\/kon.\\.e2e\\-test\\.gematik\\.solutions", "http://127.0.0.1:" + getTigerProxyConfiguration().getPort());
            return httpResponse.withBody(newBody);
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

    private class TigerProxyRouteBuildingException extends RuntimeException {
    }
}
