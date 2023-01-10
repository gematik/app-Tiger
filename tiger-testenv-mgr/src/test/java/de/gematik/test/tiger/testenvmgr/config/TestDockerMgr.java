/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;
import de.gematik.test.tiger.testenvmgr.AbstractTestTigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.env.DockerMgr;
import de.gematik.test.tiger.testenvmgr.servers.DockerServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerType;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestDockerMgr extends AbstractTestTigerTestEnvMgr {

    private static final String TEST_IMAGE_NO_HEALTHCHECK = "gematik1/tiger-test-image:1.1.0";
    private static final String TEST_IMAGE = "gematik1/tiger-test-image:1.0.0";

    private DockerMgr dmgr;
    private DockerServer server;

    @BeforeEach
    public void pullImages() {
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
    void testDockerMgrStartUpOK() {
        final CfgServer srv = new CfgServer();
        srv.setType(getTigerServerTypeDocker());
        srv.setSource(List.of(TEST_IMAGE));
        srv.setHostname("testcontainer");
        srv.setStartupTimeoutSec(15);
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            buildDockerServerFromConfiguration(envMgr,"blub1", srv);
            dmgr.startContainer(server);

            assertThat(dmgr.getContainers())
                .hasSize(1);
        });
    }

    @Test
    void testDockerMgrStartUpTooShort() {
        final CfgServer srv = new CfgServer();
        srv.setType(getTigerServerTypeDocker());
        srv.setSource(List.of(TEST_IMAGE));
        srv.setHostname("testcontainer");
        srv.setStartupTimeoutSec(2);
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            buildDockerServerFromConfiguration(envMgr,"blub2", srv);

            assertThatThrownBy(() -> dmgr.startContainer(server))
                .isInstanceOf(TigerTestEnvException.class)
                .hasMessage("Startup of server blub2 timed out after 2 seconds!");
        });
    }

    @Test
    void testDockerMgrStartupTimeoutFallback() {
        final CfgServer srv = new CfgServer();
        srv.setType(getTigerServerTypeDocker());
        srv.setSource(List.of(TEST_IMAGE_NO_HEALTHCHECK)); // has no healtchcheck
        srv.setHostname("idp5");
        srv.setStartupTimeoutSec(5); // to few seconds for startup
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            buildDockerServerFromConfiguration(envMgr,"blub3", srv);

            long startms = System.currentTimeMillis();
            dmgr.startContainer(server);
            assertThat(System.currentTimeMillis() - startms).isLessThan(20000);
        });
    }

    @Test
    void testDockerMgrPauseUnpause() {
        final CfgServer srv = new CfgServer();
        srv.setType(getTigerServerTypeDocker());
        srv.setSource(List.of(TEST_IMAGE)); // has no healtchcheck
        srv.setHostname("idp4");
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            buildDockerServerFromConfiguration(envMgr, "blub4", srv);

            dmgr.startContainer(server);
            dmgr.pauseContainer(server);
            dmgr.unpauseContainer(server);

            assertThatNoException();
        });
    }

    private static TigerServerType getTigerServerTypeDocker() {
        return DockerServer.class.getAnnotation(TigerServerType.class);
    }

    private void buildDockerServerFromConfiguration(TigerTestEnvMgr testEnvMgr, String serverId, CfgServer srv) {
        testEnvMgr.setUpEnvironment();
        server = (DockerServer) testEnvMgr.createServer(serverId, srv);
    }
}

