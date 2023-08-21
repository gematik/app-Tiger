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

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.bouncycastle.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.netty.MockServer;

import java.nio.charset.StandardCharsets;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

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
        fakeBackendServerClient.when(request().withPath("/")
            .withMethod("GET"))
            .respond(response()
                .withStatusCode(888)
                .withBody("{\"home\":\"page\"}"));
        fakeBackendServerClient.when(request().withPath("/forward.*")
                .withMethod("GET"))
            .respond(response()
                .withStatusCode(301)
                .withHeader("Location", "/foobar"));
        fakeBackendServerClient.when(request().withPath("/deep/forward.*")
                .withMethod("GET"))
            .respond(response()
                .withStatusCode(301)
                .withHeader("Location", "/deep/foobar"));
        fakeBackendServerClient.when(request().withPath("/redirect/withAdditionalHeaders")
                        .withMethod("GET"))
                .respond(response()
                        .withStatusCode(302)
                        .withHeader("Location", "/redirect/foobar")
                        .withHeader("additional-header", "test_value"));

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
