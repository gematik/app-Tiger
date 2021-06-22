/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.data.TigerRoute;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyConfigurationException;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyRouteConflictException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.mock.Expectation;
import org.mockserver.model.ExpectationId;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.MockServer;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.proxyconfiguration.ProxyConfiguration.Type;
import org.mockserver.socket.tls.NettySslContextFactory;
import wiremock.org.eclipse.jetty.util.URIUtil;

import javax.net.ssl.SSLException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;

@Slf4j
public class TigerProxy extends AbstractTigerProxy {

    private MockServer mockServer;
    private MockServerClient mockServerClient;
    private MockServerToRbelConverter mockServerToRbelConverter;
    private Map<String, TigerRoute> tigerRouteMap = new HashMap<>();
    private List<TigerRemoteProxyClient> tigerRemoteProxyClients;

    public TigerProxy(TigerProxyConfiguration configuration) {
        super(configuration);
        // TODO still checking why https connections from rustls are not working sometimes yielding the mentioned buggy behaviour
        // with bad tag exception, unsure how to reproduce it best
        // https://bugs.openjdk.java.net/browse/JDK-8221218
        // https://forum.portswigger.net/thread/complete-proxy-failure-due-to-java-tls-bug-1e334581
        //System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2,SSLv3,TLSv1.3");
        if (configuration.getServerRootCa() != null) {
            TigerKeyAndCertificateFactoryInjector.injectIntoMockServer(configuration);
        } else {
            if (StringUtils.isNotEmpty(configuration.getServerRootCaCertPem())) {
                log.info("Changing CA to file {}", configuration.getServerRootCaCertPem());
                ConfigurationProperties.certificateAuthorityCertificate(configuration.getServerRootCaCertPem());
            }
            if (StringUtils.isNotEmpty(configuration.getServerRootCaKeyPem())) {
                log.info("Changing CA-key to file {}", configuration.getServerRootCaKeyPem());
                ConfigurationProperties.certificateAuthorityPrivateKey(configuration.getServerRootCaKeyPem());
            }
        }
        mockServerToRbelConverter = new MockServerToRbelConverter(getRbelLogger());
        ConfigurationProperties.useBouncyCastleForKeyAndCertificateGeneration(true);
        if (StringUtils.isNotEmpty(configuration.getProxyLogLevel())) {
            ConfigurationProperties.logLevel(configuration.getProxyLogLevel());
        }

        NettySslContextFactory.clientSslContextBuilderFunction = sslContextBuilder -> {
            try {
                sslContextBuilder.sslContextProvider(new BouncyCastleJsseProvider());
                return sslContextBuilder.build();
            } catch (SSLException e) {
                throw new RuntimeException(e);
            }
        };
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
                .filter(existingRoute -> URIUtil.equalsIgnoreEncodings(existingRoute.getFrom(), tigerRoute.getFrom()))
                .findAny()
                .ifPresent(existingRoute -> {
                    throw new TigerProxyRouteConflictException(existingRoute);
                });

        log.info("adding route {} -> {}", tigerRoute.getFrom(), tigerRoute.getTo());
        final Expectation[] expectations = mockServerClient.when(request()
                .withHeader("Host", tigerRoute.getFrom().split("://")[1])
                .withSecure(tigerRoute.getFrom().startsWith("https://")))
                .forward(
                        req -> forwardOverriddenRequest(
                                req.replaceHeader(Header.header("Host", tigerRoute.getTo().split("://")[1])))
                                .getHttpRequest().withSecure(tigerRoute.getTo().startsWith("https://")),
                        (req, resp) -> {
                            if (!tigerRoute.isDisableRbelLogging()) {
                                try {
                                    triggerListener(mockServerToRbelConverter.convertRequest(req, tigerRoute.getFrom()));
                                    triggerListener(mockServerToRbelConverter.convertResponse(resp, tigerRoute.getFrom()));
                                } catch (Exception e) {
                                    log.error("RBel FAILED!", e);
                                }
                            }
                            return resp;
                        }
                );
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

    @Override
    public void removeRoute(String routeId) {
        mockServerClient.clear(new ExpectationId().withId(routeId));
        final TigerRoute route = tigerRouteMap.remove(routeId);

        log.info("Deleted route {}. Current # expectations {}",
                route,
                mockServerClient.retrieveActiveExpectations(request()).length);
    }
}
