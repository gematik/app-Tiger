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

import static org.assertj.core.api.Assertions.assertThat;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RequiredArgsConstructor
public class EpaTracingpointsTest {

    @LocalServerPort
    private int springBootPort;
    @Autowired
    private TigerProxy tigerProxy;
    @Value("${info.app.version:unknown}")
    public String version;

    @Test
    public void retrieveTracingpoints_shouldMatchSpecification() {
        final JsonNode tracingpointsBody = Unirest.get("http://localhost:" + springBootPort + "/tracingpoints")
            .asJson().getBody();

        assertThat(tracingpointsBody.isArray()).isTrue();
        assertThat(tracingpointsBody.getArray().length()).isGreaterThanOrEqualTo(1);
        assertThat(tracingpointsBody.getArray().getJSONObject(0).getString("name"))
            .isEqualTo("tigerProxy Tracing Point");
        assertThat(tracingpointsBody.getArray().getJSONObject(0).getInt("port"))
            .isEqualTo(tigerProxy.getProxyPort());
        assertThat(tracingpointsBody.getArray().getJSONObject(0).getString("ws_endpoint"))
            .isEqualTo("/tracing");
        assertThat(tracingpointsBody.getArray().getJSONObject(0).getString("stomp_topic"))
            .isEqualTo("/topic/traces");
        assertThat(tracingpointsBody.getArray().getJSONObject(0).getString("protocol_type"))
            .isEqualTo("tigerProxyStomp");
        assertThat(tracingpointsBody.getArray().getJSONObject(0).has("protocol_version"))
            .isTrue();
    }
}
