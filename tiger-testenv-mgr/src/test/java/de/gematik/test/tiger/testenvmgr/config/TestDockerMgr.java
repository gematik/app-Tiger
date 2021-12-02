/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import de.gematik.test.tiger.common.config.ServerType;
import de.gematik.test.tiger.testenvmgr.DockerMgr;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.servers.DockerServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

public class TestDockerMgr {

    private static final String TEST_IMAGE = "eitzenbe/test-containers:1.0.19";
    private static final String TEST_IMAGE_NO_HEALTHCHECK = "eitzenbe/test-containers:1.1.0";

    private DockerMgr dmgr;
    private DockerServer server;

    @BeforeEach
    public void pullImages() {
        TestTigerTestEnvMgr.proxySettings();

        dmgr = new DockerMgr();
        dmgr.pullImage(TEST_IMAGE);
        dmgr.pullImage(TEST_IMAGE_NO_HEALTHCHECK);
        assertThat(dmgr.getContainers())
            .isEmpty();
    }

    @AfterEach
    public void tearDown() {
        if (server != null) {
            dmgr.stopContainer(server);
        }
    }

    @Test
    public void testDockerMgrStartUpOK() {
        final CfgServer srv = new CfgServer();
        srv.setType(ServerType.DOCKER);
        srv.setSource(List.of(TEST_IMAGE));
        srv.setHostname("testcontainer");
        srv.setStartupTimeoutSec(15);
        buildDockerServerFromConfiguration(srv);
        dmgr.startContainer(server);

        assertThat(dmgr.getContainers())
            .hasSize(1);
    }

    @Test
    public void testDockerMgrStartUpTooShort() {
        final CfgServer srv = new CfgServer();
        srv.setType(ServerType.DOCKER);
        srv.setSource(List.of(TEST_IMAGE));
        srv.setHostname("testcontainer");
        srv.setStartupTimeoutSec(2);
        buildDockerServerFromConfiguration(srv);

        assertThatThrownBy(() -> dmgr.startContainer(server))
            .isInstanceOf(TigerTestEnvException.class)
            .hasMessage("Startup of server testcontainer timed out after 2 seconds!");
    }

    @Test
    public void testDockerMgrStartupTimeoutFallback() {
        // TODO ensure image with given version is available locally
        final CfgServer srv = new CfgServer();
        srv.setType(ServerType.DOCKER);
        srv.setSource(List.of(TEST_IMAGE_NO_HEALTHCHECK)); // has no healtchcheck
        srv.setHostname("idp5");
        srv.setStartupTimeoutSec(5); // to few seconds for startup
        buildDockerServerFromConfiguration(srv);

        long startms = System.currentTimeMillis();
        dmgr.startContainer(server);
        assertThat(System.currentTimeMillis() - startms).isLessThan(20000);
    }

    @Test
    public void testDockerMgrPauseUnpause() {
        // TODO ensure image with given version is available locally
        final CfgServer srv = new CfgServer();
        srv.setType(ServerType.DOCKER);
        srv.setSource(List.of(TEST_IMAGE)); // has no healtchcheck
        srv.setHostname("idp4");
        buildDockerServerFromConfiguration(srv);

        dmgr.startContainer(server);
        dmgr.pauseContainer(server);
        dmgr.unpauseContainer(server);
    }

    private void buildDockerServerFromConfiguration(CfgServer srv) {
        final TigerTestEnvMgr testEnvMgr = mock(TigerTestEnvMgr.class);
        doReturn(Configuration.builder().build())
            .when(testEnvMgr).getConfiguration();
        doAnswer(invocation -> invocation.getArguments()[0])
            .when(testEnvMgr).replaceSysPropsInString(any());
        server = (DockerServer) TigerServer.create("blub", srv, testEnvMgr);
    }
}
