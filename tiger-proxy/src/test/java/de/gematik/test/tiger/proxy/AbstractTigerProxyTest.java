/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import de.gematik.rbellogger.RbelOptions;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import java.nio.charset.StandardCharsets;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.bouncycastle.util.Arrays;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.netty.MockServer;

@Slf4j

public abstract class AbstractTigerProxyTest {

    public static MockServer fakeBackendServer;
    public static int fakeBackendServerPort = 0;
    public static byte[] binaryMessageContent = new byte[100];
    public static MockServerClient fakeBackendServerClient;
    public TigerProxy tigerProxy;
    public UnirestInstance proxyRest;

    @BeforeAll
    public static void setupBackendServer() {
        fakeBackendServer = new MockServer(TigerGlobalConfiguration
            .readIntegerOptional("free.ports.199").orElse(0));

        log.info("Started Backend-Server on port {} (http & https)", fakeBackendServer.getLocalPort());
        fakeBackendServerPort = fakeBackendServer.getLocalPort();

        fakeBackendServerClient = new MockServerClient("localhost", fakeBackendServer.getLocalPort());
        fakeBackendServerClient.when(request().withPath("/foobar.*")
                .withMethod("GET"))
            .respond(response()
                .withStatusCode(666)
                .withReasonPhrase("EVIL")
                .withHeader("foo", "bar1", "bar2")
                .withHeader("Some-Header-Field", "complicated-value §$%&/((=)(/(/&$()=§$§")
                .withBody("{\"foo\":\"bar\"}"));
        fakeBackendServerClient.when(request().withPath("/deep/foobar.*")
            .withMethod("GET"))
            .respond(response()
                .withStatusCode(777)
                .withReasonPhrase("DEEPEREVIL")
                .withHeader("foo", "bar1", "bar2")
                .withBody("{\"foo\":\"bar\"}"));

        binaryMessageContent = Arrays.concatenate(
            "This is a meaningless string which will be binary content. And some more test chars: "
                .getBytes(StandardCharsets.UTF_8),
            RandomUtils.nextBytes(100));
        fakeBackendServerClient.when(request().withPath("/foobar.*")
            .withMethod("POST"))
            .respond(response()
                .withBody(binaryMessageContent));
    }

    @AfterEach
    public void stopSpawnedTigerProxy() {
        if (tigerProxy != null) {
            log.info("Closing tigerProxy from '{}'...", this.getClass().getSimpleName());
            tigerProxy.close();
            System.gc();
        }
    }

    public void spawnTigerProxyWith(TigerProxyConfiguration configuration) {
        System.setProperty("java.util.logging.config.file", "SKIP_MOCKSERVER_LOG_INIT!");
        configuration.setProxyLogLevel("ERROR");
        configuration.setName("Primary Tiger Proxy");
        tigerProxy = new TigerProxy(configuration);

        proxyRest = Unirest.spawnInstance();
        proxyRest.config()
            .proxy("localhost", tigerProxy.getProxyPort())
            .sslContext(tigerProxy.buildSslContext());
    }

    public void awaitMessagesInTiger(int numberOfMessagesExpected) {
        await()
            .until(() -> tigerProxy.getRbelLogger().getMessageHistory().size() >= numberOfMessagesExpected);
    }


    public HttpRequest getLastRequest() {
        final HttpRequest[] loggedRequests = fakeBackendServerClient.retrieveRecordedRequests(request());
        if (loggedRequests.length == 0) {
            fail("No requests were logged!");
        }
        return loggedRequests[loggedRequests.length - 1];
    }
}
