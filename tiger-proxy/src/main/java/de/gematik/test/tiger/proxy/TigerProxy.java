/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.data.TigerRoute;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyConfigurationException;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyRouteConflictException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.buf.UriUtil;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import org.mockserver.model.ExpectationId;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.MockServer;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.proxyconfiguration.ProxyConfiguration.Type;
import org.mockserver.socket.tls.KeyAndCertificateFactoryFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;

@Slf4j
public class TigerProxy extends AbstractTigerProxy {

    private final TigerPkiIdentity serverRootCa;
    private MockServer mockServer;
    private MockServerClient mockServerClient;
    private MockServerToRbelConverter mockServerToRbelConverter;
    private Map<String, TigerRoute> tigerRouteMap = new HashMap<>();

    public TigerProxy(TigerProxyConfiguration configuration) {
        super(configuration);

        if (configuration.getServerRootCa() != null) {
            serverRootCa = configuration.getServerRootCa();
        } else {
            serverRootCa = new TigerPkiIdentity(
                "CertificateAuthorityCertificate.pem;" +
                    "PKCS8CertificateAuthorityPrivateKey.pem;" +
                    "PKCS8");
        }

        if (configuration.getServerRootCa() != null) {
            KeyAndCertificateFactoryFactory.setCustomKeyAndCertificateFactorySupplier(
                (mockServerLogger, isServerInstance) -> {
                    if (isServerInstance) {
                        return new TigerKeyAndCertificateFactory(mockServerLogger,
                            configuration.getServerRootCa(), null);
                    } else {
                        return new TigerKeyAndCertificateFactory(mockServerLogger,
                            null, configuration.getForwardMutualTlsIdentity());
                    }
                });
        }
        mockServerToRbelConverter = new MockServerToRbelConverter(getRbelLogger().getRbelConverter());
        ConfigurationProperties.useBouncyCastleForKeyAndCertificateGeneration(true);
        ConfigurationProperties.forwardProxyTLSX509CertificatesTrustManagerType("ANY");
        if (StringUtils.isNotEmpty(configuration.getProxyLogLevel())) {
            ConfigurationProperties.logLevel(configuration.getProxyLogLevel());
        }
        ConfigurationProperties.forwardProxyPrivateKey();

        mockServer = convertProxyConfiguration(configuration)
            .map(proxyConfiguration -> new MockServer(proxyConfiguration, configuration.getPortAsArray()))
            .orElseGet(() -> new MockServer(configuration.getPortAsArray()));
        log.info("Proxy started on port " + mockServer.getLocalPort());

        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort());
        if (configuration.getProxyRoutes() != null) {
            for (TigerRoute tigerRoute : configuration.getProxyRoutes()) {
                addRoute(tigerRoute);
            }
        }
        if (configuration.isActivateRbelEndpoint()) {
            addRbelTrafficEndpoint();
        }

        subscribeToTrafficEndpoints(configuration);

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
                            triggerListener(mockServerToRbelConverter.convertRequest(req, req.getSocketAddress().getScheme() + "://" + req.getSocketAddress().getHost() + ":" + req.getSocketAddress().getPort()));
                            triggerListener(mockServerToRbelConverter.convertResponse(resp, req.getSocketAddress().getScheme() + "://" + req.getSocketAddress().getHost() + ":" + req.getSocketAddress().getPort()));
                        } catch (Exception e) {
                            log.error("RBel FAILED!", e);
                        }
                        return resp;
                    });
        }
    }

    private static String patchPath(String requestPath, String forwardTarget) {
        if (StringUtils.isEmpty(forwardTarget)) {
            return requestPath;
        }
        final String patchedUrl = requestPath.replaceFirst(forwardTarget, "");
        if (patchedUrl.startsWith("/")) {
            return patchedUrl;
        } else {
            return "/" + patchedUrl;
        }
    }

    private void subscribeToTrafficEndpoints(TigerProxyConfiguration configuration) {
        Optional.of(configuration)
            .filter(Objects::nonNull)
            .map(TigerProxyConfiguration::getTrafficEndpoints)
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

    private Optional<ProxyConfiguration> convertProxyConfiguration(TigerProxyConfiguration configuration) {
        if (configuration.getForwardToProxy() == null
            || StringUtils.isEmpty(configuration.getForwardToProxy().getHostname())) {
            return Optional.empty();
        }
        return Optional.of(ProxyConfiguration.proxyConfiguration(Type.HTTPS,
            configuration.getForwardToProxy().getHostname()
                + ":"
                + configuration.getForwardToProxy().getPort()));
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
        tigerRoute.setId(expectations[0].getId());
        tigerRouteMap.put(expectations[0].getId(), tigerRoute);
        return tigerRoute;
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
                req -> {
                    final URI targetUri = new URI(tigerRoute.getTo());
                    return forwardOverriddenRequest(
                        req.withSocketAddress(
                            tigerRoute.getTo().startsWith("https://"),
                            targetUri.getHost(),
                            targetUri.getPort()
                        ))
                        .getHttpRequest().withSecure(tigerRoute.getTo().startsWith("https://"))
                        .withPath(patchPath(req.getPath().getValue(), tigerRoute.getFrom()));
                },
                buildExpectationCallback(tigerRoute, tigerRoute.getFrom())
            );
    }

    private Expectation[] buildForwardProxyRoute(TigerRoute tigerRoute) {
        return mockServerClient.when(request()
                .withHeader("Host", tigerRoute.getFrom().split("://")[1])
                .withSecure(tigerRoute.getFrom().startsWith("https://")))
            .forward(
                req -> forwardOverriddenRequest(
                    req.replaceHeader(Header.header("Host", tigerRoute.getTo().split("://")[1])))
                    .getHttpRequest().withSecure(tigerRoute.getTo().startsWith("https://")),
                buildExpectationCallback(tigerRoute, tigerRoute.getFrom())
            );
    }

    private ExpectationForwardAndResponseCallback buildExpectationCallback(TigerRoute tigerRoute, String protocolAndHost) {
        return (req, resp) -> {
            if (!tigerRoute.isDisableRbelLogging()) {
                try {
                    triggerListener(mockServerToRbelConverter.convertRequest(req, protocolAndHost));
                    triggerListener(mockServerToRbelConverter.convertResponse(resp, protocolAndHost));
                } catch (Exception e) {
                    log.error("RBel FAILED!", e);
                }
            }
            return resp;
        };
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
            ks.setCertificateEntry("caCert", serverRootCa.getCertificate());
            int chainCertCtr = 0;
            for (X509Certificate chainCert : serverRootCa.getCertificateChain()) {
                ks.setCertificateEntry("chainCert" + chainCertCtr++, chainCert);
            }
            return ks;
        } catch (Exception e) {
            throw new TigerProxyTrustManagerBuildingException("Error while building SSL-Context for tiger-proxy", e);
        }
    }

    public SSLContext buildSslContext() {
        try {
            TrustManagerFactory tmf = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm());

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
    }
}
