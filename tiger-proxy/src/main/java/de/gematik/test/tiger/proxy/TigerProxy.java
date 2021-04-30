package de.gematik.test.tiger.proxy;

import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.wiremockUtils.WiremockProxyUrlTransformer;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.SocketAddress.Scheme;
import org.mockserver.netty.MockServer;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.proxyconfiguration.ProxyConfiguration.Type;

@Slf4j
public class TigerProxy implements ITigerProxy {

    private final List<IRbelMessageListener> rbelMessageListeners = new ArrayList<>();
    private MockServer mockServer;
    private MockServerClient mockServerClient;

    public TigerProxy(TigerProxyConfiguration configuration) {
        ProxyConfiguration proxyConfiguration = ProxyConfiguration.proxyConfiguration(Type.HTTPS,
            configuration.getForwardToProxy().getHostname()
                + ":"
                + configuration.getForwardToProxy().getPort());
        mockServer = new MockServer(proxyConfiguration);
        mockServerClient = new MockServerClient("localhost", mockServer.getLocalPort());


        //https://download-ref.tsl.ti-dienste.de/ECC/ECC-RSA_TSL-ref.xml'

        mockServerClient.when(request()
                .withHeader("Host", "tsl"))
            .forward(
                forwardOverriddenRequest(
                    request()
                        .withHeader("Host", "download-ref.tsl.ti-dienste.de")
                ));

        for (Entry<String, String> routeEntry : configuration.getProxyRoutes().entrySet()) {
            addRoute(routeEntry.getKey(), routeEntry.getValue());
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
    public List<RbelElement> getRbelMessages() {
//        return rbelLogger.getMessageHistory();
        return null;
    }

    @Override
    public void addRoute(String sourceHost, String targetHost) {
        //TODO urlRegexPattern wird momentan einfach fix ausgewertet. Da müssen wir bei Gelegenheit mal drüber reden
        //TODO rbelEnabled wird ignoriert.
        log.info("adding route {} -> {}", sourceHost, targetHost);
        mockServerClient.when(request()
            .withHeader("Host", sourceHost))
            .forward(
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
