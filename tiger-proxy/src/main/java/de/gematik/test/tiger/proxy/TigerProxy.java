package de.gematik.test.tiger.proxy;

import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.mockserver.client.MockServerClient;
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

    public TigerProxy(TigerProxyConfiguration configuration) {
        mockServer = convertProxyConfiguration(configuration)
            .map(config -> new MockServer(config, 6666))
            .orElse(new MockServer());
        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort());
        rbelLogger = RbelLogger.build();

        for (Entry<String, String> routeEntry : configuration.getProxyRoutes().entrySet()) {
            addRoute(routeEntry.getKey(), routeEntry.getValue());
        }
    }

    @NotNull
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
    public void addRoute(String sourceHost, String targetHost) {
        //TODO urlRegexPattern wird momentan einfach fix ausgewertet. Da müssen wir bei Gelegenheit mal drüber reden
        //TODO rbelEnabled wird ignoriert.
        log.info("adding route {} -> {}", sourceHost, targetHost);
        mockServerClient.when(request()
            .withHeader("Host", sourceHost))
            .forward(
                //  forward().withScheme("http")
                forwardOverriddenRequest(
                    request()
                        .withHeader("Host", targetHost)
                ));
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
