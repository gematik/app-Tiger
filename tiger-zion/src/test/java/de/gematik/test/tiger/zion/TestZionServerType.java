package de.gematik.test.tiger.zion;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.ZionServerType;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.zion.config.ZionConfiguration;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xmlunit.assertj.XmlAssert;

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

    @TigerTest(tigerYaml = """
        servers:
          mainServer:
            type: zion
            zionConfiguration:
              serverPort: "${free.port.50}"
              mockResponses:
                passwordCheckResponse:
                  backendRequests:
                    tokenCheck:
                      url: "http://localhost:${free.port.60}/checkPassword"
                      body: '?{$.header.password}'
                      assignments:
                        correctPassword: "!{$.responseCode == '200'}"
                  requestCriterions:
                    - message.method == 'GET'
                    - message.path == '/helloWorld'
                  nestedResponses:
                    correctPassword:
                      importance: 10
                      requestCriterions:
                        - "${correctPassword} == 'true'"
                      response:
                        statusCode: 200
                        body: '{"Hello":"World"}'
                    wrongPassword:
                      importance: 0
                      response:
                        statusCode: 405
                        body: '{"Wrong":"Password"}'
          backendServer:
            type: zion
            zionConfiguration:
              serverPort: "${free.port.60}"
              mockResponses:
                correctPassword:
                  importance: 10
                  requestCriterions:
                    - "$.body == 'secret'"
                  response:
                    statusCode: 200
                wrongPassword:
                  importance: 0
                  response:
                    statusCode: 400
        """)
    @Test
    void testMultipleZionServer(UnirestInstance unirest) {
        final HttpResponse<JsonNode> response = unirest.get(TigerGlobalConfiguration.resolvePlaceholders(
                "http://mainServer/helloWorld"
            ))
            .header("password", "secret")
            .asJson();

        assertThat(response.getStatus())
            .isEqualTo(200);
    }

    @TigerTest(tigerYaml = """
        servers:
          mainServer:
            type: zion
            zionConfiguration:
              serverPort: "${free.port.50}"
              mockResponses:
                hello:
                    response:
                        statusCode: 666
                        body: "<hello value='Wöüärld'></hello>"
        """)
    @Test
    void zionShouldNotMessUpUnicodeCharactersWhenNoExplicitEncodingSet(UnirestInstance unirest) {
        final HttpResponse<String> response = unirest.get(TigerGlobalConfiguration.resolvePlaceholders(
            "http://mainServer/helloWorld"
        )).asString();

        XmlAssert.assertThat(response.getBody()).and("""
                <?xml version="1.0" encoding="UTF-8"?>
                                
                <hello value="Wöüärld"></hello>""")
            .areIdentical();
    }

    @TigerTest(tigerYaml = """
        servers:
          mainServer:
            type: zion
            zionConfiguration:
              serverPort: "${free.port.50}"
              mockResponses:
                hello:
                    response:
                        statusCode: 666
                        body: "<hello value='Wöüärld'></hello>"
        """)
    @ParameterizedTest
    /*
    Why not more examples you ask? For that please have a look at XMLEntityManager::getEncodingInfo()
    The Java SAX-Parser determines the encoding NOT based on the stated encoding-information, but rather
    using the first four bytes of a document. Thus 'latin-1' will not work.
     */
    @ValueSource(strings = {"UTF-8", "UTF-16"})
    void zionShouldNotMessUpUnicodeCharactersWhenNoExplicitEncodingSet(String charsetName,
        UnirestInstance unirest, TigerTestEnvMgr tigerTestEnvMgr) {
        final Charset selectedCharset = Charset.forName(charsetName);
        final String xmlWithCharset = new String(("<?xml version=\"1.0\" encoding=\"" + charsetName + "\"?>\n" +
                                      "<hello value=\"Wöüärld\"></hello>").getBytes(selectedCharset),selectedCharset);
        ((ZionServerType) tigerTestEnvMgr.getServers().get("mainServer"))
            .getApplicationContext().getBean(ZionConfiguration.class)
            .getMockResponses().get("hello").getResponse()
            .setBody(xmlWithCharset);

        final HttpResponse<String> response = unirest.get(TigerGlobalConfiguration.resolvePlaceholders(
            "http://mainServer/helloWorld"
        )).asString();

        XmlAssert.assertThat(response.getBody()).and(xmlWithCharset)
            .areIdentical();
    }
}
