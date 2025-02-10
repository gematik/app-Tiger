/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.testenvmgr;

import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.LOCAL_PROXY_ADMIN_PORT;
import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.LOCAL_PROXY_PROXY_PORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.AbstractTigerServer;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import kong.unirest.UnirestInstance;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Slf4j
@Getter
class TestEnvManagerPositive extends AbstractTestTigerTestEnvMgr {

  // -----------------------------------------------------------------------------------------------------------------
  //
  // check minimum configurations pass the check and MVP configs are started successfully,
  // check hostname is set to key if missing
  //

  @ParameterizedTest
  @ValueSource(strings = {"testTigerProxy", "testExternalJar", "testExternalUrl"})
  void testCheckCfgPropertiesMinimumConfigPasses_OK(String cfgFileName) {
    log.info("Starting testCheckCfgPropertiesMinimumConfigPasses_OK for {}", cfgFileName);
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.initializeWithCliProperties(
        Map.of(
            "TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFileName + ".yaml"));

    createTestEnvMgrSafelyAndExecute(
        envMgr -> {
          CfgServer srv = envMgr.getConfiguration().getServers().get(cfgFileName);
          envMgr.createServer("blub", srv).assertThatConfigurationIsCorrect();
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"testTigerProxy", "testExternalJarMVP", "testExternalUrl"})
  void testSetUpEnvironmentNShutDownMinimumConfigPasses_OK(String cfgFileName) throws IOException {
    log.info("Starting testSetUpEnvironmentNShutDownMinimumConfigPasses_OK for {}", cfgFileName);
    TigerGlobalConfiguration.reset();
    createTestEnvMgrSafelyAndExecute(
        envMgr -> {
          envMgr.setUpEnvironment();
        },
        "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFileName + ".yaml");
  }

  @Test
  void testHostnameIsAutosetIfMissingK() {
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.initializeWithCliProperties(
        Map.of(
            "TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testServerWithNoHostname.yaml"));

    createTestEnvMgrSafelyAndExecute(
        envMgr -> {
          envMgr.setUpEnvironment();
          AbstractTigerServer srv = envMgr.getServers().get("testServerWithNoHostname");
          assertThat(srv.getHostname()).isEqualTo("testServerWithNoHostname");
        });
  }

  // -----------------------------------------------------------------------------------------------------------------
  //
  // externalUrl details
  //

  @Test
  void testExternalUrlViaProxy() {
    assertThatNoException()
        .isThrownBy(
            () -> {
              TigerGlobalConfiguration.reset();
              TigerGlobalConfiguration.initializeWithCliProperties(
                  Map.of(
                      "TIGER_TESTENV_CFGFILE",
                      "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalUrl.yaml"));
              createTestEnvMgrSafelyAndExecute(TigerTestEnvMgr::setUpEnvironment);
            });
  }

  @Test
  @TigerTest(
      tigerYaml =
          "servers:\n"
              + "  externalJarServer:\n"
              + "    type: externalJar\n"
              + "    source:\n"
              + "      - \"http://localhost:${wiremock.port}/download\"\n"
              + "    healthcheckUrl: http://127.0.0.1:${free.port.0}\n"
              + "    healthcheckReturnCode: 200\n"
              + "    externalJarOptions:\n"
              + "      arguments:\n"
              + "        - \"--httpPort=${free.port.0}\"\n"
              + "        - \"--webroot=.\"\n")
  void workingDirNotSet_ShouldDefaultToOsTempDirectory(TigerTestEnvMgr envMgr) {
    final String workingDir =
        envMgr
            .getServers()
            .get("externalJarServer")
            .getConfiguration()
            .getExternalJarOptions()
            .getWorkingDir();
    assertThat(new File(workingDir))
        .exists()
        .isDirectoryContaining(file -> file.getName().equals("download"))
        .isDirectoryContaining(file -> file.getName().equals("download.dwnProps"));
  }

  @Test
  @TigerTest(
      tigerYaml =
          "servers:\n"
              + "  externalJarServer:\n"
              + "    type: externalJar\n"
              + "    source:\n"
              + "      - \"http://localhost:${wiremock.port}/download\"\n"
              + "    healthcheckUrl: http://127.0.0.1:${free.port.0}/foo/bar/wrong/url\n"
              + "    healthcheckReturnCode: 200\n"
              + "    startupTimeoutSec: 1\n"
              + "    externalJarOptions:\n"
              + "      arguments:\n"
              + "        - \"--httpPort=${free.port.0}\"\n"
              + "        - \"--webroot=.\"\n",
      skipEnvironmentSetup = true)
  void healthcheckEndpointGives404AndExpecting200_environmentShouldNotStartUp(
      TigerTestEnvMgr envMgr) {
    executeWithSecureShutdown(
        () -> {
          assertThatThrownBy(envMgr::setUpEnvironment)
              .isInstanceOf(TigerEnvironmentStartupException.class)
              .cause()
              .isInstanceOf(TigerTestEnvException.class)
              .hasMessageContaining("/foo/bar/wrong/url")
              .hasMessageContaining("Timeout");
        },
        envMgr);
  }

  // -----------------------------------------------------------------------------------------------------------------
  //
  // local tiger proxy details
  //

  @Test
  @TigerTest(
      tigerYaml =
          """
            servers:
              externalJarServer:
                type: externalJar
                source:
                  - "http://localhost:${wiremock.port}/download"
                healthcheckUrl: http://127.0.0.1:${free.port.0}
                healthcheckReturnCode: 200
                externalJarOptions:
                  arguments:
                    - "--httpPort=${free.port.0}"
                    - "--webroot=."

            """,
      skipEnvironmentSetup = true)
  void startLocalTigerProxyAndCheckPropertiesSet(TigerTestEnvMgr envMgr) {
    envMgr.startLocalTigerProxyIfActivated();
    assertThat(LOCAL_PROXY_ADMIN_PORT.getValueOrDefault()).isBetween(0, 655536);
    assertThat(LOCAL_PROXY_PROXY_PORT.getValueOrDefault()).isBetween(0, 655536);
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
            tigerProxy:
              proxyPort: ${free.port.1}
              adminPort: ${free.port.2}
            servers:
              externalJarServer:
                type: externalJar
                source:
                  - "http://localhost:${wiremock.port}/download"
                healthcheckUrl: http://127.0.0.1:${free.port.0}
                healthcheckReturnCode: 200
                externalJarOptions:
                  arguments:
                    - "--httpPort=${free.port.0}"
                    - "--webroot=."
            """,
      skipEnvironmentSetup = true)
  void startLocalTigerProxyWithConfiguredPortsAndCheckPropertiesMatch() {
    assertThat(LOCAL_PROXY_ADMIN_PORT.getValueOrDefault())
        .isEqualTo(TigerGlobalConfiguration.readIntegerOptional("free.port.2").get());
    assertThat(LOCAL_PROXY_PROXY_PORT.getValueOrDefault())
        .isEqualTo(TigerGlobalConfiguration.readIntegerOptional("free.port.1").get());
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
            additionalConfigurationFiles:
              - filename: src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalJar.yaml
                baseKey: tiger

            """)
  void readAdditionalYamlFiles(UnirestInstance unirestInstance) {
    assertThat(unirestInstance.get("http://testExternalJar").asString().isSuccess()).isTrue();
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
            additionalConfigurationFiles:
              - filename: src/test/resources/de/gematik/test/tiger/testenvmgr/externalJarWithAdditionalTigerKey.yaml
            """)
  void readAdditionalYamlFilesWithoutBaseKey(UnirestInstance unirestInstance) {
    assertThat(unirestInstance.get("http://testExternalJar").asString().isSuccess()).isTrue();
  }

  private void executeWithSecureShutdown(Runnable test, TigerTestEnvMgr envMgr) {
    try {
      test.run();
    } finally {
      envMgr.shutDown();
    }
  }
}
