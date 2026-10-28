/*
 * Copyright 2021-2026 gematik GmbH
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */

package de.gematik.test.tiger.testenvmgr.servers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.rbellogger.util.GlobalServerMap;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.AbstractTestTigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import org.junit.jupiter.api.Test;

class ExternalUrlServerTest extends AbstractTestTigerTestEnvMgr {

  @Test
  @TigerTest(
      tigerYaml =
          """
          servers:
            myExternalUrlServer:
              type: externalUrl
              source:
                - https://www.example.com/dingsbums
          """)
  void routeOfExternalUrlServerShallHaveFullPath(TigerTestEnvMgr envMgr) {

    var externalUrlRoute =
        envMgr.getLocalTigerProxyOrFail().getRoutes().stream()
            .filter(r -> "http://myExternalUrlServer".equals(r.getFrom()))
            .findAny();
    assertThat(externalUrlRoute).isPresent();
    assertThat(externalUrlRoute.get().getFrom()).isEqualTo("http://myExternalUrlServer");
    assertThat(externalUrlRoute.get().getTo()).isEqualTo("https://www.example.com/dingsbums");
    assertThat(GlobalServerMap.getServerNameForPort(443)).isEmpty();
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
          servers:
            httpbin:
              type: httpbin
              serverPort: ${free.port.0}
            popp-client:
              type: externalUrl
              dependsUpon: httpbin
              source:
                - http://localhost:${free.port.1}
              healthcheckUrl: http://localhost:${free.port.0}/status/200
          """)
  void aLocalExternalUrlServerShouldNameItsPort(TigerTestEnvMgr envMgr) {
    assertThat(
            GlobalServerMap.getServerNameForPort(
                TigerGlobalConfiguration.readIntegerOptional("free.port.1").orElseThrow()))
        .contains("popp-client");
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
          tigerProxy:
            forwardToProxy:
              hostname: localhost
              port: ${free.port.2}
              noProxyHosts:
                - 127.0.0.2
          servers:
            httpbin:
              type: httpbin
              serverPort: ${free.port.0}
            popp-client:
              type: externalUrl
              dependsUpon: httpbin
              source:
                - http://localhost:${free.port.1}
              healthcheckUrl: http://127.0.0.2:${free.port.0}/status/200
          """)
  void healthCheckShouldReachAHostOnTheNoProxyListDirectly(TigerTestEnvMgr envMgr) {
    assertThat(envMgr.getServers()).containsKey("popp-client");
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
          tigerProxy:
            forwardToProxy:
              hostname: localhost
              port: ${free.port.2}
          servers:
            httpbin:
              type: httpbin
              serverPort: ${free.port.0}
            popp-client:
              type: externalUrl
              dependsUpon: httpbin
              source:
                - http://localhost:${free.port.1}
              healthcheckUrl: http://127.0.0.2:${free.port.0}/status/200
          """,
      skipEnvironmentSetup = true)
  void healthCheckShouldUseTheForwardProxyForAHostNotOnTheNoProxyList(TigerTestEnvMgr envMgr) {
    assertThatThrownBy(envMgr::setUpEnvironment)
        .isInstanceOf(TigerEnvironmentStartupException.class);
  }
}
