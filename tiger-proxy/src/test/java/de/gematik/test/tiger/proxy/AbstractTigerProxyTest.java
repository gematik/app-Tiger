/*
 * Copyright (c) 2021 gematik GmbH
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

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import de.gematik.rbellogger.converter.RbelJexlExecutor;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.modifier.RbelModificationDescription;
import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.SocketAddress;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;

@Slf4j
public abstract class AbstractTigerProxyTest {

    @Rule
    public MockServerRule forwardProxy = new MockServerRule(this);
    @Rule
    public WireMockRule fakeBackendServer = new WireMockRule(options()
        .dynamicPort()
        .dynamicHttpsPort());
    public TigerProxy tigerProxy;
    public UnirestInstance proxyRest;
    public byte[] binaryMessageContent = new byte[100];

    @Before
    public void setupBackendServer() {
        log.info("Started Backend-Server on ports {} and {} (https)", fakeBackendServer.port(), fakeBackendServer.httpsPort());
        log.info("Started Forward-Proxy-Server on port {}", forwardProxy.getPort());

        fakeBackendServer.stubFor(get(urlEqualTo("/foobar"))
            .willReturn(aResponse()
                .withStatus(666)
                .withStatusMessage("EVIL")
                .withHeader("foo", "bar1", "bar2")
                .withBody("{\"foo\":\"bar\"}")));
        fakeBackendServer.stubFor(get(urlEqualTo("/deep/foobar"))
            .willReturn(aResponse()
                .withStatus(777)
                .withStatusMessage("DEEPEREVIL")
                .withHeader("foo", "bar1", "bar2")
                .withBody("{\"foo\":\"bar\"}")));
        ThreadLocalRandom.current().nextBytes(binaryMessageContent);
        fakeBackendServer.stubFor(post(urlEqualTo("/foobar"))
            .willReturn(aResponse()
                .withBody(binaryMessageContent)));

        forwardProxy.getClient().when(request())
            .forward(
                req -> forwardOverriddenRequest(
                    req.withSocketAddress(
                        "localhost", fakeBackendServer.port(), SocketAddress.Scheme.HTTP
                    ))
                    .getHttpRequest());

        RbelJexlExecutor.activateJexlDebugging();
        Unirest.config().reset();
    }

    public void spawnTigerProxyWith(TigerProxyConfiguration configuration) {
        tigerProxy = new TigerProxy(configuration);

        proxyRest = Unirest.spawnInstance();
        proxyRest.config()
            .proxy("localhost", tigerProxy.getPort())
            .sslContext(tigerProxy.buildSslContext());
    }
}
