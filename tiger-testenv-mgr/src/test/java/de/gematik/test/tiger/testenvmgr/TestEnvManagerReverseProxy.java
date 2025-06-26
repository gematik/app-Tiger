/*
 *
 * Copyright 2021-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.testenvmgr;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import java.text.MessageFormat;
import kong.unirest.core.Unirest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
@Getter
class TestEnvManagerReverseProxy extends AbstractTestTigerTestEnvMgr {

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
              - http://localhost:${wiremock.port}/download
            externalJarOptions:
              arguments:
                - --httpPort=${free.port.0}
                - --webroot=.
          reverseproxy1:
            type: tigerProxy
            tigerProxyConfiguration:
              adminPort: ${free.port.2}
              proxiedServer: testWinstone2
              proxyPort: ${free.port.3}
       """)
  void testReverseProxy() {
    final kong.unirest.core.HttpResponse<String> httpResponse =
        Unirest.get(
                "http://127.0.0.1:"
                    + TigerGlobalConfiguration.readStringOptional("free.port.3").get())
            .asString();
    assertThat(httpResponse.getBody().trim())
        .withFailMessage(
            MessageFormat.format(
                "Expected to receive folder index page from Winstone server, but got HTTP {0} with"
                    + " body \n"
                    + "'{1}'",
                httpResponse.getStatus(), httpResponse.getBody()))
        .startsWith("<!DOCTYPE html>")
        .endsWith("</html>");
  }

  @Test
  @TigerTest(
      cfgFilePath =
          "src/test/resources/de/gematik/test/tiger/testenvmgr/testReverseProxyManual.yaml")
  void testReverseProxyManual() {
    log.info("Entering test");
    final kong.unirest.core.HttpResponse<String> httpResponse =
        Unirest.get(
                "http://127.0.0.1:"
                    + TigerGlobalConfiguration.readStringOptional("free.port.2").get())
            .asString();
    assertThat(httpResponse.getBody().trim())
        .withFailMessage(
            MessageFormat.format(
                "Expected to receive folder index page from Winstone server, but got HTTP {0} with"
                    + " body \n"
                    + "{1}",
                httpResponse.getStatus(), httpResponse.getBody()))
        .startsWith("<!DOCTYPE html>")
        .endsWith("</html>")
        .contains("<h1 class=\"title\">Directory:");
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
        servers:
          testWinstone2:
            type: externalJar
            source:
              - http://localhost:${wiremock.port}/download
            healthcheckUrl: http://127.0.0.1:${free.port.0}/target
            healthcheckReturnCode: 200
            externalJarOptions:
              workingDir: target/
              arguments:
                - --httpPort=${free.port.0}
                - --webroot=..
          reverseproxy1:
            type: tigerProxy
            tigerProxyConfiguration:
              adminPort: ${free.port.2}
              proxiedServer: testWinstone2
              proxyPort: ${free.port.3}
        """)
  void deepPathHealthcheckUrl_routeShouldTargetBaseUrl() {
    final kong.unirest.core.HttpResponse<String> httpResponse =
        Unirest.get(
                "http://127.0.0.1:"
                    + TigerGlobalConfiguration.readStringOptional("free.port.3").get())
            .asString();
    assertThat(httpResponse.getBody()).contains("<title>Directory: /</title>");
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
        servers:
          testWinstone2:
            type: externalJar
            source:
              - http://localhost:${wiremock.port}/download
            healthcheckUrl: http://127.0.0.1:${free.port.0}
            healthcheckReturnCode: 200
            externalJarOptions:
              arguments:
                - --httpPort=${free.port.0}
                - --webroot=..
          proxykon2:
            type: tigerProxy
            active: true
            tigerProxyConfiguration:
              adminPort: ${free.port.1}
              proxyPort: ${free.port.2}
              tls:
                serverIdentity: "src/test/resources/c.ak.aut-konsim.p12;00"
        """)
  void remoteProxyWithConfiguredTlsIdentity() {
    var response =
        Unirest.get(
                "http://127.0.0.1:"
                    + TigerGlobalConfiguration.readStringOptional("free.port.0").get())
            .asString();
    assertThat(response.isSuccess()).isTrue();
  }
}
