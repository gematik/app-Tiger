/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.fail;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.matching.RequestPattern;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import de.gematik.rbellogger.RbelOptions;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;

@Slf4j
public abstract class AbstractTigerProxyTest {

    public WireMockServer fakeBackendServer;
    public TigerProxy tigerProxy;
    public UnirestInstance proxyRest;
    public byte[] binaryMessageContent = new byte[100];

    @BeforeEach
    public void setupBackendServer() {
        if (fakeBackendServer != null) {
            return;
        }

        fakeBackendServer = new WireMockServer(
            new WireMockConfiguration()
                .dynamicPort()
                .dynamicHttpsPort());
        fakeBackendServer.start();

        log.info("Started Backend-Server on ports {} and {} (https)", fakeBackendServer.port(),
            fakeBackendServer.httpsPort());

        fakeBackendServer.stubFor(get(urlPathEqualTo("/foobar"))
            .willReturn(aResponse()
                .withStatus(666)
                .withStatusMessage("EVIL")
                .withHeader("foo", "bar1", "bar2")
                .withBody("{\"foo\":\"bar\"}")));
        fakeBackendServer.stubFor(get(urlPathEqualTo("/deep/foobar"))
            .willReturn(aResponse()
                .withStatus(777)
                .withStatusMessage("DEEPEREVIL")
                .withHeader("foo", "bar1", "bar2")
                .withBody("{\"foo\":\"bar\"}")));

        ThreadLocalRandom.current().nextBytes(binaryMessageContent);
        fakeBackendServer.stubFor(post(urlPathEqualTo("/foobar"))
            .willReturn(aResponse()
                .withBody(binaryMessageContent)));

        RbelOptions.activateJexlDebugging();
        Unirest.config().reset();
    }

    public void spawnTigerProxyWith(TigerProxyConfiguration configuration) {
        tigerProxy = new TigerProxy(configuration);

        proxyRest = Unirest.spawnInstance();
        proxyRest.config()
            .proxy("localhost", tigerProxy.getProxyPort())
            .sslContext(tigerProxy.buildSslContext());
    }

    public LoggedRequest getLastRequest() {
        final List<LoggedRequest> loggedRequests = fakeBackendServer.findRequestsMatching(RequestPattern.everything())
            .getRequests();
        if (loggedRequests.isEmpty()) {
            fail("No requests were logged!");
        }
        return loggedRequests.get(loggedRequests.size() - 1);
    }
}
