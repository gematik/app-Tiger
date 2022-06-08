/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.servers.log;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrNormalized;
import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.common.config.ServerType;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.servers.ExternalJarServer;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class TigerServerLogManagerTest {

    @Test
    public void testCheckAddAppenders_OK() throws Exception {
        String log = "This is a test log!";
        String logFile = "./target/serverLogs/test.log";
        String serverID = "ExternalJar-001";
        final CfgServer configuration = new CfgServer();
        configuration.setType(ServerType.EXTERNALJAR);
        configuration.setLogFile(logFile);
        ExternalJarServer server = ExternalJarServer.builder().serverId(serverID).configuration(configuration).build();
        TigerServerLogManager.addAppenders(server);
        Logger dummyLog = server.getLog();
        String text = tapSystemErrNormalized(() -> {
            System.err.println(log);
        });
        dummyLog.info(log);
        assertThat(IOUtils.toString(new File(logFile).toURI(), StandardCharsets.UTF_8)).contains(log);
        assertThat(text).contains(log);
        assertThat(dummyLog.getName()).isEqualTo("TgrSrv-" +serverID);
    }
}