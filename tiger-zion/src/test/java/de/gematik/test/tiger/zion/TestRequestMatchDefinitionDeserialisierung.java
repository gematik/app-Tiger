/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.zion;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import kong.unirest.HttpResponse;
import kong.unirest.HttpStatus;
import kong.unirest.Unirest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@ResetTigerConfiguration
class TestRequestMatchDefinitionDeserialisierung {

    @BeforeEach
    @AfterEach
    public void resetConfig() {
        TigerGlobalConfiguration.reset();
    }

    /**
     * This test purpose is to make sure that the {@link de.gematik.test.tiger.zion.config.ZionRequestMatchDefinition}
     * is properly deserialized into the zion configuration. More detailed tests to the path matching functionality are
     * in {@link TestPathVariables}.
     */
    @TigerTest(tigerYaml = """
            servers:
              zionServer:
                type: zion
                zionConfiguration:
                  serverPort: "${free.port.0}"
                  mockResponses:
                    testMapFromRequestIntoCriterias:
                      request:
                        path: "/foobar/{myVar}"
                        method: "GET"
                        additionalCriterions:
                          - "'${myVar}' == 'valueToAssign'"
                      response:
                        statusCode: 200
                        body: "${myVar}"
                """)
    @Test
    void testDeserialisierung() {
        HttpResponse<String> response = Unirest.get(
                TigerGlobalConfiguration.resolvePlaceholders("http://localhost:${free.port.0}/foobar/valueToAssign")
        ).asString();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("valueToAssign");
    }
}
