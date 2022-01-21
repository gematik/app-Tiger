/*
 * Copyright (c) 2022 gematik GmbH
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
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.SocketAddress;

@Slf4j
public class TestTigerProxyHeavyDuty {

    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this);
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(options()
        .stubRequestLoggingDisabled(true)
        .dynamicPort());

    @Before
    public void setupBackendServer() {
        Set<String> artifactoryLoggers = new HashSet<>(Arrays.asList("org.apache.http", "groovyx.net.http"));
        for(String log:artifactoryLoggers) {
            ch.qos.logback.classic.Logger artLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(log);
            artLogger.setLevel(ch.qos.logback.classic.Level.INFO);
            artLogger.setAdditive(false);
        }

        wireMockRule.stubFor(post(urlEqualTo("/postSomething"))
            .willReturn(aResponse()
                .withStatus(666)));

        mockServerRule.getClient().when(request())
            .forward(
                req -> forwardOverriddenRequest(
                    req.withSocketAddress(
                        "localhost", wireMockRule.port(), SocketAddress.Scheme.HTTP
                    ))
                    .getHttpRequest());
    }

    @Test
    @Ignore("Lasttest. Scheitert momentan noch an Wiremock")
    public void useAsWebProxyServer_shouldForward() {
        final TigerProxy tigerProxy = new TigerProxy(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + wireMockRule.port())
                .build()))
            .proxyLogLevel("WARN")
            .activateRbelParsing(false)
            .build());

        final UnirestInstance unirestInstance = Unirest.spawnInstance();
        unirestInstance.config().proxy("localhost", tigerProxy.getPort());

        final String bigDatString = RandomStringUtils.randomAlphanumeric(20 * 1024 * 1024);

        for (int i = 0; i < 100; i++) {
            log.info("Sending another message...");
            unirestInstance.post("http://backend/postSomething")
                .body(bigDatString)
                .asJson();
        }
    }
}
