package de.gematik.test.tiger.proxy;

import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;

import com.github.tomakehurst.wiremock.http.HttpHeader;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHttpRequest;
import de.gematik.rbellogger.data.RbelHttpResponse;
import de.gematik.rbellogger.data.RbelMultiValuedMapElement;
import de.gematik.rbellogger.data.RbelPathElement;
import de.gematik.rbellogger.data.RbelStringElement;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.Header;
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
        rbelLogger = RbelLogger.build();
        mockServerToRbelConverter = new MockServerToRbelConverter(rbelLogger);

        mockServer = convertProxyConfiguration(configuration)
            .map(config -> new MockServer(config, 6666))
            .orElse(new MockServer());
        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort());

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
                req -> forwardOverriddenRequest(
                    req.replaceHeader(Header.header("Host", targetHost))
                ).getHttpRequest(),
                (req, resp) -> {
                    triggerListener(mockServerToRbelConverter.convertRequest(req));
                    triggerListener(mockServerToRbelConverter.convertResponse(resp));
                    return resp;
                }
            );
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
