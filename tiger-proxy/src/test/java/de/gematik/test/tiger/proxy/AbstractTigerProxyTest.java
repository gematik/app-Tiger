/*
 * Copyright (c) 2023 gematik GmbH
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.matching.RequestPattern;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import de.gematik.rbellogger.RbelOptions;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.bouncycastle.util.Arrays;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

@Slf4j
public abstract class AbstractTigerProxyTest {

    public static WireMockServer fakeBackendServer;
    public static byte[] binaryMessageContent = new byte[100];
    public TigerProxy tigerProxy;
    public UnirestInstance proxyRest;

    @BeforeAll
    public static void setupBackendServer() {
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

        binaryMessageContent = Arrays.concatenate(
            "This is a meaningless string which will be binary content. And some more test chars: "
                .getBytes(StandardCharsets.UTF_8),
            RandomUtils.nextBytes(100));
        fakeBackendServer.stubFor(post(urlPathEqualTo("/foobar"))
            .willReturn(aResponse()
                .withBody(binaryMessageContent)));

        RbelOptions.activateJexlDebugging();
    }

    @AfterAll
    public static void stopWiremock() {
        fakeBackendServer.stop();
    }

    @AfterEach
    public void stopSpawnedTigerProxy() throws Exception {
        if (tigerProxy != null) {
            log.info("Closing tigerProxy from '{}'...", this.getClass().getSimpleName());
            tigerProxy.close();
            tigerProxy.shutdown();
            System.gc();
        }
    }

    public void spawnTigerProxyWith(TigerProxyConfiguration configuration) {
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


    public LoggedRequest getLastRequest() {
        final List<LoggedRequest> loggedRequests = fakeBackendServer.findRequestsMatching(RequestPattern.everything())
            .getRequests();
        if (loggedRequests.isEmpty()) {
            fail("No requests were logged!");
        }
        return loggedRequests.get(loggedRequests.size() - 1);
    }
}
