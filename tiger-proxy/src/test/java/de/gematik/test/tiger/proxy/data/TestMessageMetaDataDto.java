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
package de.gematik.test.tiger.proxy.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import static org.mockserver.model.HttpRequest.request;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.proxy.AbstractTigerProxyTest;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.SocketAddress;
import org.mockserver.netty.MockServer;

@Slf4j
public class TestMessageMetaDataDto extends AbstractTigerProxyTest {

    public MockServerClient forwardProxy;

    @BeforeEach
    public void setupForwardProxy() {
        if (forwardProxy != null) {
            return;
        }

        final MockServer forwardProxyServer = new MockServer();

        forwardProxy = new MockServerClient("localhost", forwardProxyServer.getLocalPort());
        log.info("Started Forward-Proxy-Server on port {}", forwardProxy.getPort());

        forwardProxy.when(request())
            .forward(
                req -> forwardOverriddenRequest(
                    req.withSocketAddress(
                        "localhost", fakeBackendServer.port(), SocketAddress.Scheme.HTTP
                    ))
                    .getHttpRequest());
    }

    @Test
    public void checkMessageMetaDataDtoConversion()  {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());

        proxyRest.get("http://backend/foobar").asJson();

        MessageMetaDataDto message0 = MessageMetaDataDto.createFrom(tigerProxy.getRbelMessages().get(0));
        assertThat(message0.path).isEqualTo("/foobar");
        assertThat(message0.method).isEqualTo("GET");
        assertThat(message0.responseCode).isNull();
        assertThat(message0.recipient).isEqualTo("backend:80");
        assertThat(message0.sender).matches("(view-|)localhost:\\d*");
        assertThat(message0.sequenceNumber).isEqualTo(0);

        MessageMetaDataDto message1 = MessageMetaDataDto.createFrom(tigerProxy.getRbelMessages().get(1));
        assertThat(message1.path).isNull();
        assertThat(message1.method).isNull();
        assertThat(message1.responseCode).isEqualTo(666);
        assertThat(message1.recipient).matches("(view-|)localhost:\\d*");
        assertThat(message1.sender).isEqualTo("backend:80");
        assertThat(message1.sequenceNumber).isEqualTo(1);
    }
}
