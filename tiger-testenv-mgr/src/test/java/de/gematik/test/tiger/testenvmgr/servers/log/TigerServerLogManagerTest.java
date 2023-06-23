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

import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.servers.ExternalJarServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerType;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.stream.SystemErr;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.org.webcompere.systemstubs.SystemStubs.tapSystemErrNormalized;

@Slf4j
class TigerServerLogManagerTest {

    @SystemStub
    private SystemErr systemErr;

    @Test
    void testCheckAddAppenders_OK() throws Exception {
        String logMessage = "This is a test log!";
        String logFile = "target/serverLogs/test.log";
        String serverID = "ExternalJar-001";
        final CfgServer configuration = new CfgServer();
        configuration.setType(ExternalJarServer.class.getAnnotation(TigerServerType.class));
        configuration.setLogFile(logFile);
        ExternalJarServer server = ExternalJarServer.builder().serverId(serverID).configuration(configuration).build();
        TigerServerLogManager.addAppenders(server);
        Logger dummyLog = server.getLog();
        String text = tapSystemErrNormalized(() -> System.err.println(logMessage));
        dummyLog.info(logMessage);

        File folder = Path.of("target/serverLogs").toFile();
        log.info("Directory exists: " + (folder.exists() && folder.isDirectory()));
        assertThat(new File(logFile)).content().contains(logMessage);
        if (folder.list() != null) {
            log.info("Files inside: " + String.join(", ", folder.list()));
        } else {
            Assertions.fail("Folder is invalid! " + folder.getAbsolutePath());
        }
        assertThat(dummyLog.getName()).isEqualTo("TgrSrv-" +serverID);
        assertThat(text).contains(logMessage);
    }
}
