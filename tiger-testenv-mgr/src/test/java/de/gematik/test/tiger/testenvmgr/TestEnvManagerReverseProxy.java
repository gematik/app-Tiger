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

package de.gematik.test.tiger.testenvmgr;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import kong.unirest.Unirest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
@Getter
public class TestEnvManagerReverseProxy extends AbstractTestTigerTestEnvMgr {

  @Test
  @TigerTest(
      tigerYaml =
          """
        servers:
          testWinstone2:
            type: externalJar
            healthcheckUrl: http://127.0.0.1:${free.port.0}
            healthcheckReturnCode: 200
            source:
              - http://localhost:${mockserver.port}/download
            externalJarOptions:
              arguments:
                - --httpPort=${free.port.0}
                - --webroot=.
          reverseproxy1:
            type: tigerProxy
            tigerProxyCfg:
              adminPort: ${free.port.2}
              proxiedServer: testWinstone2
              proxyPort: ${free.port.3}
                """)
  void testReverseProxy() {
    final kong.unirest.HttpResponse<String> httpResponse =
        Unirest.get(
                "http://127.0.0.1:"
                    + TigerGlobalConfiguration.readStringOptional("free.port.3").get())
            .asString();
    assertThat(httpResponse.getBody().trim())
        .withFailMessage(
            "Expected to receive folder index page from Winstone server, but got HTTP "
                + httpResponse.getStatus()
                + " with body \n"
                + httpResponse.getBody())
        .startsWith("<HTML>")
        .endsWith("</HTML>");
  }

  @Test
  @TigerTest(
      cfgFilePath =
          "src/test/resources/de/gematik/test/tiger/testenvmgr/testReverseProxyManual.yaml")
  void testReverseProxyManual() {
    log.info("Entering test");
    final kong.unirest.HttpResponse<String> httpResponse =
        Unirest.get(
                "http://127.0.0.1:"
                    + TigerGlobalConfiguration.readStringOptional("free.port.2").get())
            .asString();
    assertThat(httpResponse.getBody().trim())
        .withFailMessage(
            "Expected to receive folder index page from Winstone server, but got HTTP "
                + httpResponse.getStatus()
                + " with body \n"
                + httpResponse.getBody())
        .startsWith("<HTML>")
        .endsWith("</HTML>")
        .contains("<H1>Directory:");
  }

  @Test
  @TigerTest(
      tigerYaml =
          "servers:\n"
              + "  testWinstone2:\n"
              + "    type: externalJar\n"
              + "    source:\n"
              + "      - http://localhost:${mockserver.port}/download\n"
              + "    healthcheckUrl: http://127.0.0.1:${free.port.0}/target\n"
              + "    healthcheckReturnCode: 200\n"
              + "    externalJarOptions:\n"
              + "      workingDir: target/\n"
              + "      arguments:\n"
              + "        - --httpPort=${free.port.0}\n"
              + "        - --webroot=..\n"
              + "  reverseproxy1:\n"
              + "    type: tigerProxy\n"
              + "    tigerProxyCfg:\n"
              + "      adminPort: ${free.port.2}\n"
              + "      proxiedServer: testWinstone2\n"
              + "      proxyPort: ${free.port.3}\n")
  void deepPathHealthcheckUrl_routeShouldTargetBaseUrl() {
    final kong.unirest.HttpResponse<String> httpResponse =
        Unirest.get(
                "http://127.0.0.1:"
                    + TigerGlobalConfiguration.readStringOptional("free.port.3").get())
            .asString();
    assertThat(httpResponse.getBody()).contains("<TITLE>Directory: /</TITLE>");
  }

  @Test
  @TigerTest(
      tigerYaml =
          "servers:\n"
              + "  testWinstone2:\n"
              + "    type: externalJar\n"
              + "    source:\n"
              + "      - http://localhost:${mockserver.port}/download\n"
              + "    healthcheckUrl: http://127.0.0.1:${free.port.0}\n"
              + "    healthcheckReturnCode: 200\n"
              + "    externalJarOptions:\n"
              + "      arguments:\n"
              + "        - --httpPort=${free.port.0}\n"
              + "        - --webroot=..\n"
              + "  proxykon2:\n"
              + "    type: tigerProxy\n"
              + "    active: true\n"
              + "    tigerProxyCfg:\n"
              + "      adminPort: ${free.port.1}\n"
              + "      proxyPort: ${free.port.2}\n"
              + "      tls:\n"
              + "        serverIdentity: \"src/test/resources/c.ak.aut-konsim.p12;00\"")
  void remoteProxyWithConfiguredTlsIdentity() {
    var response =
        Unirest.get(
                "http://127.0.0.1:"
                    + TigerGlobalConfiguration.readStringOptional("free.port.0").get())
            .asString();
    assertThat(response.isSuccess()).isTrue();
  }
}
