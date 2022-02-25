/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

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

import static org.assertj.core.api.Assertions.assertThat;

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
            .isEqualTo(tigerProxy.getPort());
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
