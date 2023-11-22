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

import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.LOCAL_PROXY_ADMIN_PORT;
import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.LOCAL_PROXY_PROXY_PORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.AbstractTigerServer;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import kong.unirest.UnirestInstance;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
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
    FileUtils.deleteDirectory(new File("WinstoneHTTPServer"));
    createTestEnvMgrSafelyAndExecute(
        envMgr -> {
          envMgr.setUpEnvironment();
        },
        "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFileName + ".yaml");
  }

  @Test
  void testHostnameIsAutosetIfMissingK() {
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
              TigerGlobalConfiguration.initializeWithCliProperties(
                  Map.of(
                      "TIGER_TESTENV_CFGFILE",
                      "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalUrl.yaml"));
              createTestEnvMgrSafelyAndExecute(TigerTestEnvMgr::setUpEnvironment);
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"withPkiKeys", "withUrlMappings"})
  void testExternalUrl_withDetails(String cfgFileName) {
    assertThatNoException()
        .isThrownBy(
            () -> {
              TigerGlobalConfiguration.initializeWithCliProperties(
                  Map.of(
                      "TIGER_TESTENV_CFGFILE",
                      "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalUrl_"
                          + cfgFileName
                          + ".yaml"));
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
              + "      - \"http://localhost:${mockserver.port}/download\"\n"
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
              + "      - \"http://localhost:${mockserver.port}/download\"\n"
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
          assertThatThrownBy(() -> envMgr.setUpEnvironment())
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
          "servers:\n"
              + "  externalJarServer:\n"
              + "    type: externalJar\n"
              + "    source:\n"
              + "      - \"http://localhost:${mockserver.port}/download\"\n"
              + "    healthcheckUrl: http://127.0.0.1:${free.port.0}\n"
              + "    healthcheckReturnCode: 200\n"
              + "    externalJarOptions:\n"
              + "      arguments:\n"
              + "        - \"--httpPort=${free.port.0}\"\n"
              + "        - \"--webroot=.\"\n",
      skipEnvironmentSetup = true)
  void startLocalTigerProxyAndCheckPropertiesSet(TigerTestEnvMgr envMgr) {
    envMgr.startLocalTigerProxyIfActivated();
    assertThat(LOCAL_PROXY_ADMIN_PORT.getValueOrDefault()).isBetween(0, 655536);
    assertThat(LOCAL_PROXY_PROXY_PORT.getValueOrDefault()).isBetween(0, 655536);
  }

  @Test
  @TigerTest(
      tigerYaml =
          "tigerProxy:\n"
              + "  proxyPort: ${free.port.1}\n"
              + "  adminPort: ${free.port.2}\n"
              + "servers:\n"
              + "  externalJarServer:\n"
              + "    type: externalJar\n"
              + "    source:\n"
              + "      - \"http://localhost:${mockserver.port}/download\"\n"
              + "    healthcheckUrl: http://127.0.0.1:${free.port.0}\n"
              + "    healthcheckReturnCode: 200\n"
              + "    externalJarOptions:\n"
              + "      arguments:\n"
              + "        - \"--httpPort=${free.port.0}\"\n"
              + "        - \"--webroot=.\"\n",
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
          "additionalYamls:\n"
              + "  - filename:"
              + " src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalJar.yaml\n"
              + "    baseKey: tiger\n")
  void readAdditionalYamlFiles(UnirestInstance unirestInstance) {
    assertThat(unirestInstance.get("http://testExternalJar").asString().isSuccess()).isTrue();
  }

  @Test
  @TigerTest(
      tigerYaml =
          "additionalYamls:\n"
              + "  - filename:"
              + " src/test/resources/de/gematik/test/tiger/testenvmgr/externalJarWithAdditionalTigerKey.yaml\n")
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
