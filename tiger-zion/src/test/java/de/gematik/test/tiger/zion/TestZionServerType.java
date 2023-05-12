package de.gematik.test.tiger.zion;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.junit.jupiter.api.Test;

 class TestZionServerType {

    @TigerTest(tigerYaml = "servers:\n"
        + "  zionServer:\n"
        + "    type: zion\n"
        + "    zionConfiguration:\n"
        + "      serverPort: \"${free.port.10}\"\n"
        + "      mockResponses:\n"
        + "        helloWorld:\n"
        + "          requestCriterions:\n"
        + "            - message.method == 'GET'\n"
        + "            - message.url =~ '.*/helloWorld'\n"
        + "          response:\n"
        + "            statusCode: 222\n"
        + "            body: '{\"Hello\":\"World\"}'\n")
    @Test
    void testZionServer() {
        final HttpResponse<JsonNode> response = Unirest.get(TigerGlobalConfiguration.resolvePlaceholders(
            "http://localhost:${free.port.10}/blubBlab/helloWorld"
        )).asJson();

        assertThat(response.getStatus())
            .isEqualTo(222);
        assertThat(response.getBody().getObject().getString("Hello"))
            .isEqualTo("World");
    }
}
