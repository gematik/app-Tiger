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

package de.gematik.test.tiger.testenvmgr.servers.log;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.org.webcompere.systemstubs.SystemStubs.tapSystemErrNormalized;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.CfgExternalJarOptions;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.ExternalJarServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerType;
import java.io.File;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;

@Slf4j
class TigerServerLogManagerTest {

  @BeforeEach
  @AfterEach
  void setup() {
    TigerGlobalConfiguration.reset();
    Path.of("target", "serverLogs").toFile().deleteOnExit();
  }

  @SystemStub private SystemErr systemErr;

  @Test
  void testCheckAddAppenders_OK() throws Exception {
    String logMessage = "This is a test log!";
    String logFile = "target/serverLogs/test.log";
    String serverID = "ExternalJar-001";
    final CfgServer configuration = new CfgServer();
    configuration.setExternalJarOptions(new CfgExternalJarOptions());
    configuration.getExternalJarOptions().setActivateLogs(true);
    configuration.setType(ExternalJarServer.class.getAnnotation(TigerServerType.class).value());
    configuration.setLogFile(logFile);
    ExternalJarServer server =
        ExternalJarServer.builder().serverId(serverID).configuration(configuration).build();
    TigerServerLogManager.addAppenders(server);
    Logger dummyLog = server.getLog();
    String text = tapSystemErrNormalized(() -> System.err.println(logMessage));
    dummyLog.info(logMessage);

    assertThat(new File(logFile)).content().contains(logMessage);
    assertThat(dummyLog.getName()).isEqualTo("TgrSrv-" + serverID);
    assertThat(text).contains(logMessage);
  }

  @TigerTest(
      tigerYaml =
          """
            localProxyActive: false
            servers:
              externalJarServer:
                type: externalJar
                source:
                  - local:winstone.jar
                healthcheckUrl: http://127.0.0.1:${free.port.0}
                healthcheckReturnCode: 200
                logFile: target/serverLogs/test1.log
                externalJarOptions:
                  workingDir: target
                  arguments:
                    - "--httpPort=${free.port.0}"
                    - "--webroot=."
            """)
  @Test
  void testCheckAddAppendersEnabledLog_OK() throws Exception {
    assertThat(new File("target/serverLogs/test1.log"))
        .content()
        .contains("Winstone Servlet Engine ");
  }

  @TigerTest(
      tigerYaml =
          """
            localProxyActive: false
            servers:
              externalJarServer:
                type: externalJar
                source:
                  - local:winstone.jar
                healthcheckUrl: http://127.0.0.1:${free.port.0}
                healthcheckReturnCode: 200
                logFile: target/serverLogs/test2.log
                externalJarOptions:
                  activateLogs: false
                  workingDir: target
                  arguments:
                    - "--httpPort=${free.port.0}"
                    - "--webroot=."
            """)
  @Test
  void testCheckAddAppendersDisabledLog_OK() {
    assertThat(new File("target/serverLogs/test2.log")).doesNotExist();
  }

  @TigerTest(
      tigerYaml =
          """
            localProxyActive: false
            servers:
              externalJarServer:
                type: externalJar
                source:
                  - local:winstone.jar
                healthcheckUrl: http://127.0.0.1:${free.port.0}
                healthcheckReturnCode: 200
                logFile: target/serverLogs/test3.log
                externalJarOptions:
                  activateWorkflowLogs: false
                  workingDir: target
                  arguments:
                    - "--httpPort=${free.port.0}"
                    - "--webroot=."
            """)
  @Test
  void testCheckAddAppendersDisabledOnlyWorkflowUiLog_OK() {
    assertThat(new File("target/serverLogs/test3.log"))
        .content()
        .contains("Winstone Servlet Engine ");
  }

  @TigerTest(
      tigerYaml =
          """
            localProxyActive: false
            servers:
              externalJarServer:
                type: externalJar
                source:
                  - local:winstone.jar
                healthcheckUrl: http://127.0.0.1:${free.port.0}
                healthcheckReturnCode: 200
                logFile: target/serverLogs/test4.log
                externalJarOptions:
                  activateLogs: false
                  activateWorkflowLogs: true
                  workingDir: target
                  arguments:
                    - "--httpPort=${free.port.0}"
                    - "--webroot=."
            """)
  @Test
  void testCheckAddAppendersDisabledAllButOnlyWorkflowUiLog_OK() {
    assertThat(new File("target/serverLogs/test4.log")).doesNotExist();
  }
}
