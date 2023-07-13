package de.gematik.test.tiger.zion;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ResetTigerConfiguration
class TestZionServerType {

    @BeforeEach
    @AfterEach
    public void resetConfig() {
        TigerGlobalConfiguration.reset();
    }

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
    @TigerTest(tigerYaml = "servers:\n"
        + "  zionExternal:\n"
        + "    type: externalJar\n"
        + "    healthcheckUrl:\n"
        + "      http://127.0.0.1:${free.port.10}\n"
        + "    externalJarOptions:\n"
        + "      arguments:\n"
        + "        - --server.port=${free.port.10}\n"
        + "        - --spring.profiles.active=echoserver\n"
        + "      workingDir: src/test/resources\n"
        + "    source:\n"
        + "      - local:../../../target/tiger-zion-*-executable.jar\n")
    @Test
    void testExternalZionServer(UnirestInstance unirest) {
        final HttpResponse<JsonNode> response = unirest.get(TigerGlobalConfiguration.resolvePlaceholders(
            "http://localhost:${free.port.10}/blubBlab/helloWorld"
        )).asJson();

        assertThat(response.getStatus())
            .isEqualTo(222);
        assertThat(response.getBody().getObject().getString("Hello"))
            .isEqualTo("World");
    }

    @TigerTest(tigerYaml = "servers:\n"
        + "  mainServer:\n"
        + "    type: zion\n"
        + "    zionConfiguration:\n"
        + "      serverPort: \"${free.port.50}\"\n"
        + "      mockResponses:\n"
        + "        passwordCheckResponse:\n"
        + "          backendRequests:\n"
        + "            tokenCheck:\n"
        + "              url: \"http://localhost:${free.port.60}/checkPassword\"\n"
        + "              body: '?{$.header.password}'\n"
        + "              assignments:\n"
        + "                correctPassword: \"$.responseCode == '200'\"\n"
        + "          requestCriterions:\n"
        + "            - message.method == 'GET'\n"
        + "            - message.path == '/helloWorld'\n"
        + "          nestedResponses:\n"
        + "            correctPassword:\n"
        + "              importance: 10\n"
        + "              requestCriterions:\n"
        + "                - \"${correctPassword} == 'true'\"\n"
        + "              response:\n"
        + "                statusCode: 200\n"
        + "                body: '{\"Hello\":\"World\"}'\n"
        + "            wrongPassword:\n"
        + "              importance: 0\n"
        + "              response:\n"
        + "                statusCode: 405\n"
        + "                body: '{\"Wrong\":\"Password\"}'\n"
        + "  backendServer:\n"
        + "    type: zion\n"
        + "    zionConfiguration:\n"
        + "      serverPort: \"${free.port.60}\"\n"
        + "      mockResponses:\n"
        + "        correctPassword:\n"
        + "          importance: 10\n"
        + "          requestCriterions:\n"
        + "            - \"$.body == 'secret'\"\n"
        + "          response:\n"
        + "            statusCode: 200\n"
        + "        wrongPassword:\n"
        + "          importance: 0\n"
        + "          response:\n"
        + "            statusCode: 400\n")
    @Test
    void testMultipleZionServer() {
        final HttpResponse<JsonNode> response = Unirest.get(TigerGlobalConfiguration.resolvePlaceholders(
                "http://localhost:${free.port.50}/helloWorld"
            ))
            .header("password", "secret")
            .asJson();

        assertThat(response.getStatus())
            .isEqualTo(200);
    }
}
