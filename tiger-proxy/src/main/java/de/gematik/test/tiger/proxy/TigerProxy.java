/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.converter.RbelConfiguration;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import java.security.Key;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.client.MockServerClient;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.model.Header;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.MockServer;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.proxyconfiguration.ProxyConfiguration.Type;

@Slf4j
@Data
public class TigerProxy implements ITigerProxy {

    private final List<IRbelMessageListener> rbelMessageListeners = new ArrayList<>();
    private MockServer mockServer;
    private MockServerClient mockServerClient;
    private RbelLogger rbelLogger;
    private MockServerToRbelConverter mockServerToRbelConverter;

    public TigerProxy(TigerProxyConfiguration configuration) {

        // TODO still checking why https connections from rustls are not working sometimes yielding the mentioned buggy behaviour
        // with bad tag exception, unsure how to reproduce it best
        // https://bugs.openjdk.java.net/browse/JDK-8221218
        // https://forum.portswigger.net/thread/complete-proxy-failure-due-to-java-tls-bug-1e334581
        //System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2,SSLv3,TLSv1.3");
        rbelLogger = RbelLogger.build(buildRbelLoggerConfiguration(configuration));
        mockServerToRbelConverter = new MockServerToRbelConverter(rbelLogger);
        ConfigurationProperties.useBouncyCastleForKeyAndCertificateGeneration(true);
        if (StringUtils.isNotEmpty(configuration.getServerRootCaCertPem())) {
            ConfigurationProperties.certificateAuthorityCertificate(configuration.getServerRootCaCertPem());
        }
        if (StringUtils.isNotEmpty(configuration.getServerRootCaKeyPem())) {
            ConfigurationProperties.certificateAuthorityPrivateKey(configuration.getServerRootCaKeyPem());
        }
        if (StringUtils.isNotEmpty(configuration.getProxyLogLevel())) {
            ConfigurationProperties.logLevel(configuration.getProxyLogLevel());
        }

        mockServer = convertProxyConfiguration(configuration)
            .map(MockServer::new)
            .orElse(new MockServer());
        log.info("Proxy started on port " + mockServer.getLocalPort());
        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort());
        if (configuration.getProxyRoutes() != null) {
            for (Entry<String, String> routeEntry : configuration.getProxyRoutes().entrySet()) {
                addRoute(routeEntry.getKey(), routeEntry.getValue());
            }
        }
        if (configuration.isActivateRbelEndpoint()) {
            mockServerClient.when(request()
                .withHeader("Host", "rbel"))
                .respond(HttpResponse.response()
                    .withHeader("content-type", "text/html; charset=utf-8")
                    .withBody(new RbelHtmlRenderer().doRender(rbelLogger.getMessageHistory())));
            mockServerClient.when(request()
                .withHeader("Host", null)
                .withPath("/rbel"))
                .respond(httpRequest ->
                        HttpResponse.response()
                            .withHeader("content-type", "text/html; charset=utf-8")
                            .withBody(new RbelHtmlRenderer().doRender(rbelLogger.getMessageHistory())));
        }
    }

    private RbelConfiguration buildRbelLoggerConfiguration(TigerProxyConfiguration configuration) {
        final RbelConfiguration rbelConfiguration = new RbelConfiguration();
        if (configuration.getKeyFolders() != null) {
            configuration.getKeyFolders().stream()
                .forEach(folder -> rbelConfiguration.addInitializer(new RbelKeyFolderInitializer(folder)));
        }
        return rbelConfiguration;
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
    public List<RbelElement> getRbelMessages() {
        return rbelLogger.getMessageHistory();
    }

    @Override
    public void addKey(String keyid, Key key) {
        rbelLogger.getRbelKeyManager().addKey(keyid, key, RbelKey.PRECEDENCE_KEY_FOLDER);
    }

    @Override
    public void addRoute(final String sourceSchemeNHost, final String targetSchemeNHost) {
        log.info("adding route {} -> {}", sourceSchemeNHost, targetSchemeNHost);
        mockServerClient.when(request()
            .withHeader("Host", sourceSchemeNHost.split("://")[1])
            .withSecure(sourceSchemeNHost.startsWith("https://")))
            .forward(
                req -> forwardOverriddenRequest(
                    req.replaceHeader(Header.header("Host", targetSchemeNHost.split("://")[1])))
                    .getHttpRequest().withSecure(targetSchemeNHost.startsWith("https://")),
                (req, resp) -> {
                    try {
                        triggerListener(mockServerToRbelConverter.convertRequest(req, sourceSchemeNHost));
                        triggerListener(mockServerToRbelConverter.convertResponse(resp));
                    } catch (Exception e) {
                        log.error("RBel FAILED!", e);
                    }
                    return resp;
                }
            );
    }

    @Override
    public void removeRoute(String sourceHost) {
        // TODO how to remove a route?
    }

    private void triggerListener(RbelElement element) {
        getRbelMessageListeners()
            .forEach(listener -> listener.triggerNewReceivedMessage(element));
    }

    @Override
    public void addRbelMessageListener(IRbelMessageListener listener) {
        rbelMessageListeners.add(listener);
    }

    @Override
    public void removeRbelMessageListener(IRbelMessageListener listener) {
        rbelMessageListeners.remove(listener);
    }
}
