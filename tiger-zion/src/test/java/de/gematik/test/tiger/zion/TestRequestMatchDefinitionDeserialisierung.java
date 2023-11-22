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

package de.gematik.test.tiger.zion;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.zion.config.ZionServerConfiguration;
import java.util.Map;
import kong.unirest.HttpResponse;
import kong.unirest.HttpStatus;
import kong.unirest.Unirest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ResetTigerConfiguration
class TestRequestMatchDefinitionDeserialisierung {

  @BeforeEach
  @AfterEach
  public void resetConfig() {
    TigerGlobalConfiguration.reset();
  }

  private static final String TEST_YAML =
      """
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
                """;

  /**
   * This test purpose is to make sure that the {@link
   * de.gematik.test.tiger.zion.config.ZionRequestMatchDefinition} is properly deserialized into the
   * zion configuration. More detailed tests to the path matching functionality are in {@link
   * TestPathVariables}.
   */
  @TigerTest(tigerYaml = TEST_YAML)
  @Test
  void testDeserialisierung() {
    HttpResponse<String> response =
        Unirest.get(
                TigerGlobalConfiguration.resolvePlaceholders(
                    "http://localhost:${free.port.0}/foobar/valueToAssign"))
            .asString();

    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("valueToAssign");
  }

  /**
   * In the {@link de.gematik.test.tiger.ZionServerType} the ZionServerConfiguration gets serialized
   * into JSON and converted into a map to be passed into the SpringApplicationBuilder.properties().
   * This test checks that the yaml configuration gets properly converted.
   */
  @Test
  @TigerTest(tigerYaml = TEST_YAML)
  void testSerialisierung() {
    ZionServerConfiguration zionConfiguration =
        TigerGlobalConfiguration.instantiateConfigurationBean(
                ZionServerConfiguration.class, "tiger.servers.zionServer")
            .get();

    Map<String, String> zionConfigurationMap =
        TigerSerializationUtil.toMap(zionConfiguration, "tiger.servers.zionServer");

    assertThat(zionConfigurationMap)
        .containsAllEntriesOf(
            Map.of(
                "tiger.servers.zionserver.zionconfiguration.mockresponses.testmapfromrequestintocriterias.request.path",
                    "/foobar/{myVar}",
                "tiger.servers.zionserver.zionconfiguration.mockresponses.testmapfromrequestintocriterias.request.method",
                    "GET",
                "tiger.servers.zionserver.zionconfiguration.mockresponses.testmapfromrequestintocriterias.request.additionalcriterions.0",
                    "'${myVar}' == 'valueToAssign'"));
  }
}
