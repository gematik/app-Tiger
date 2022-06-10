/*
 * Copyright (c) 2022 gematik GmbH
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

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrNormalized;
import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.common.config.ServerType;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.servers.ExternalJarServer;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

@Slf4j
class TigerServerLogManagerTest {

    @Test
    public void testCheckAddAppenders_OK() throws Exception {
        String logMessage = "This is a test log!";
        String logFile = "target/serverLogs/test.log";
        String serverID = "ExternalJar-001";
        final CfgServer configuration = new CfgServer();
        configuration.setType(ServerType.EXTERNALJAR);
        configuration.setLogFile(logFile);
        ExternalJarServer server = ExternalJarServer.builder().serverId(serverID).configuration(configuration).build();
        TigerServerLogManager.addAppenders(server);
        Logger dummyLog = server.getLog();
        String text = tapSystemErrNormalized(() -> System.err.println(logMessage));
        dummyLog.info(logMessage);
        log.info("Directory existst: " + Path.of("target/serverLogs").toFile().exists());
        log.info("Files inside: " + String.join(", ", new File("target/serverLogs").list()));
        assertThat(new File(logFile))
            .content().contains(logMessage);
        assertThat(text).contains(logMessage);
        assertThat(dummyLog.getName()).isEqualTo("TgrSrv-" +serverID);
    }
}
