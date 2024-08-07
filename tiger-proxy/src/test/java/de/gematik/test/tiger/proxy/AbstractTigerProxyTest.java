/*
 * Copyright (c) 2024 gematik GmbH
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

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.responseDefinition;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import de.gematik.rbellogger.data.facet.RbelParsingNotCompleteFacet;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import kong.unirest.Config;
import kong.unirest.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.bouncycastle.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

@Slf4j
@WireMockTest(httpsEnabled = true)
public abstract class AbstractTigerProxyTest {

  public static int fakeBackendServerPort = 0;
  public static byte[] binaryMessageContent = new byte[100];
  public TigerProxy tigerProxy;
  public UnirestInstance proxyRest;

  @BeforeEach
  public void setupBackendServer(WireMockRuntimeInfo runtimeInfo) {
    fakeBackendServerPort = runtimeInfo.getHttpPort();
    final MappingBuilder foobar =
        get(urlMatching("/foobar.*"))
            .willReturn(
                ok().withStatus(666)
                    .withStatusMessage("EVIL")
                    .withHeader("foo", "bar1", "bar2")
                    .withHeader("Some-Header-Field", "complicated-value §$%&/((=)(/(/&$()=§$§")
                    .withHeader("fooValue", "{{request.query.foo}}")
                    .withBody("{\"foo\":\"bar\"}")
                    .withTransformers("response-template"));
    runtimeInfo.getWireMock().register(stubFor(foobar));

    final MappingBuilder deepFoobar =
        get(urlMatching("/deep/foobar.*"))
            .willReturn(
                ok().withStatus(777)
                    .withStatusMessage("DEEPEREVIL")
                    .withHeader("foo", "bar1", "bar2")
                    .withBody("{\"foo\":\"bar\"}"));
    runtimeInfo.getWireMock().register(stubFor(deepFoobar));

    runtimeInfo
        .getWireMock()
        .register(
            stubFor(get("/").willReturn(ok().withStatus(888).withBody("{\"home\":\"page\"}"))));

    runtimeInfo
        .getWireMock()
        .register(stubFor(get(urlMatching("/forward.*")).willReturn(permanentRedirect("/foobar"))));
    runtimeInfo
        .getWireMock()
        .register(
            stubFor(
                get(urlMatching("/deep/forward.*")).willReturn(permanentRedirect("/deep/foobar"))));
    runtimeInfo
        .getWireMock()
        .register(
            stubFor(
                get("/redirect/withAdditionalHeaders")
                    .willReturn(
                        temporaryRedirect("/redirect/foobar")
                            .withHeader("additional-header", "test_value"))));

    binaryMessageContent =
        Arrays.concatenate(
            "This is a meaningless string which will be binary content. And some more test chars: "
                .getBytes(StandardCharsets.UTF_8),
            RandomUtils.nextBytes(100));

    runtimeInfo
        .getWireMock()
        .register(stubFor(post("/foobar").willReturn(ok().withBody(binaryMessageContent))));

    runtimeInfo
        .getWireMock()
        .register(
            stubFor(
                post("/echo")
                    .willReturn(
                        status(200)
                            .withStatusMessage("")
                            .withBody("{{request.body}}")
                            .withTransformers("response-template"))));

    runtimeInfo
        .getWireMock()
        .register(
            stubFor(
                get("/error")
                    .willReturn(responseDefinition().withFault(Fault.CONNECTION_RESET_BY_PEER))));
  }

  @BeforeEach
  public void logTestName(TestInfo testInfo) {
    log.info(
        "Now executing test '{}' ({}:{})",
        testInfo.getDisplayName(),
        testInfo.getTestClass().map(Class::getName).orElse("<>"),
        testInfo.getTestMethod().map(Method::getName).orElse("<>"));
  }

  @AfterEach
  public void stopSpawnedTigerProxy() {
    if (tigerProxy != null) {
      log.info("Closing tigerProxy from '{}'...", this.getClass().getSimpleName());
      tigerProxy.close();
    }
  }

  public void spawnTigerProxyWith(TigerProxyConfiguration configuration) {
    configuration.setProxyLogLevel("ERROR");
    configuration.setName("Primary Tiger Proxy");
    tigerProxy = new TigerProxy(configuration);

    proxyRest =
        new UnirestInstance(
            new Config()
                .proxy("localhost", tigerProxy.getProxyPort())
                .sslContext(tigerProxy.buildSslContext())
                .connectTimeout(1000 * 1000)
                .socketTimeout(1000 * 1000)
                .automaticRetries(false));
  }

  public void awaitMessagesInTiger(int numberOfMessagesExpected) {
    await()
        .atMost(5, TimeUnit.SECONDS)
        .until(
            () ->
                tigerProxy.getRbelLogger().getMessageHistory().stream()
                        .filter(el -> !el.hasFacet(RbelParsingNotCompleteFacet.class))
                        .count()
                    >= numberOfMessagesExpected);
  }

  public LoggedRequest getLastRequest(WireMock wireMock) {
    final List<ServeEvent> serveEvents = wireMock.getServeEvents();
    if (serveEvents.isEmpty()) {
      fail("Tried to get info on last request. None were found however!");
    }
    return serveEvents.get(serveEvents.size() - 1).getRequest();
  }
}
