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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;

import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.File;
import java.io.IOException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

@Slf4j
@Getter
class TestExternalJarServer extends AbstractTestTigerTestEnvMgr {
  @Test
  @TigerTest(
      tigerYaml =
          """
    servers:
      testExternalJarMVP:
        type: externalJar
        source:
          - local:winstone.jar
        healthcheckUrl: http://127.0.0.1:${free.port.0}
        healthcheckReturnCode: 200
        externalJarOptions:
          workingDir: 'target/'
          arguments:
            - --httpPort=${free.port.0}
            - --webroot=.
    """,
      skipEnvironmentSetup = true)
  void testCreateExternalJarRelativePathWithWorkingDir(TigerTestEnvMgr envMgr) {
    assertThatNoException()
        .isThrownBy(() -> executeWithSecureShutdown(envMgr::setUpEnvironment, envMgr));
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
            servers:
              testExternalJarMVP:
                type: externalJar
                source:
                  - local:target/winstone.jar
                healthcheckUrl: http://127.0.0.1:${free.port.0}
                healthcheckReturnCode: 200
                externalJarOptions:
                  arguments:
                    - --httpPort=${free.port.0}
                    - --webroot=.
            """,
      skipEnvironmentSetup = true)
  void testCreateExternalJarRelativePathWithoutWorkingDir(TigerTestEnvMgr envMgr) {
    assertThatNoException()
        .isThrownBy(() -> executeWithSecureShutdown(envMgr::setUpEnvironment, envMgr));
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
            servers:
              testExternalJarMVP:
                type: externalJar
                source:
                  - local:target/winstone.jar
                healthcheckUrl: http://127.0.0.1:${free.port.0}
                healthcheckReturnCode: 200
                externalJarOptions:
                  workingDir: '.'
                  arguments:
                    - --httpPort=${free.port.0}
                    - --webroot=.

            """,
      skipEnvironmentSetup = true)
  void testCreateExternalJarRelativePathWithRelativeWorkingDir(TigerTestEnvMgr envMgr) {
    assertThatNoException()
        .isThrownBy(
            () -> {
              executeWithSecureShutdown(envMgr::setUpEnvironment, envMgr);
            });
  }

  @Test
  void testCreateExternalJarNonExistingWorkingDir() throws IOException {
    File folder = new File("NonExistingFolder");
    if (folder.exists()) {
      FileUtils.deleteDirectory(folder);
    }

    createTestEnvMgrSafelyAndExecute(
        envMgr -> {
          CfgServer srv = envMgr.getConfiguration().getServers().get("testExternalJarMVP");
          srv.getExternalJarOptions().setWorkingDir("NonExistingFolder");
          srv.setHealthcheckUrl("NONE");
          srv.setStartupTimeoutSec(1);
          try {
            assertThatNoException().isThrownBy(envMgr::setUpEnvironment);

          } finally {
            FileUtils.forceDeleteOnExit(folder);
          }
        },
        "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalJarMVP.yaml");
  }

  @Test
  @TigerTest(
      cfgFilePath = "src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalJarMVP.yaml",
      additionalProperties = {
        "tiger.servers.testExternalJarMVP.source.0=local:miniJarWHICHDOESNOTEXIST.jar",
        "tiger.servers.testExternalJarMVP.externalJarOptions.workingDir=src/test/resources"
      },
      skipEnvironmentSetup = true)
  void testCreateExternalJarRelativePathFileNotFound(TigerTestEnvMgr envMgr) {
    executeWithSecureShutdown(
        () -> {
          assertThatThrownBy(envMgr::setUpEnvironment)
              .isInstanceOf(TigerEnvironmentStartupException.class)
              .cause()
              .isInstanceOf(TigerTestEnvException.class)
              .hasMessageStartingWith("Local jar-file")
              .hasMessageContaining("miniJarWHICHDOESNOTEXIST.jar")
              .hasMessageEndingWith(" not found!");
        },
        envMgr);
  }

  private void executeWithSecureShutdown(Runnable test, TigerTestEnvMgr envMgr) {
    try {
      test.run();
    } finally {
      envMgr.shutDown();
    }
  }
}
