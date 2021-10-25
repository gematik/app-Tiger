/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import de.gematik.test.tiger.common.config.ServerType;
import de.gematik.test.tiger.testenvmgr.DockerMgr;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvException;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestDockerMgr {

    private static final String TEST_IMAGE = "eitzenbe/test-containers:1.0.19";
    private static final String TEST_IMAGE_NO_HEALTHCHECK = "eitzenbe/test-containers:1.1.0";

    Configuration cfg = new Configuration();

    @BeforeAll
    public static void pullImages() {
        TestTigerTestEnvMgr.proxySettings();
        final DockerMgr dmgr = new DockerMgr();
        dmgr.pullImage(TEST_IMAGE);
        dmgr.pullImage(TEST_IMAGE_NO_HEALTHCHECK);
        assertThat(dmgr.getContainers())
            .isEmpty();
    }

    @Test
    public void testDockerMgrStartUpOK() {
        final DockerMgr dmgr = new DockerMgr();
        final CfgServer srv = new CfgServer();
        srv.setType(ServerType.DOCKER);
        srv.setSource(List.of(TEST_IMAGE));
        srv.setHostname("testcontainer");
        srv.setStartupTimeoutSec(15);
        dmgr.startContainer(srv, cfg, null);
        assertThat(dmgr.getContainers())
            .hasSize(1);
        dmgr.stopContainer(srv);
    }

    @Test
    public void testDockerMgrStartUpTooShort() {
        final DockerMgr dmgr = new DockerMgr();
        final CfgServer srv = new CfgServer();
        srv.setType(ServerType.DOCKER);
        srv.setSource(List.of(TEST_IMAGE));
        srv.setHostname("testcontainer");
        srv.setStartupTimeoutSec(2);
        try {
            assertThatThrownBy(() -> dmgr.startContainer(srv, cfg, null))
                .isInstanceOf(TigerTestEnvException.class)
                .hasMessage("Startup of server testcontainer timed out after 2 seconds!");
        } finally {
            dmgr.stopContainer(srv);
        }

    }

    @Test
    public void testDockerMgrStartupTimeoutFallback() {
        // TODO ensure image with given version is available locally
        final DockerMgr dmgr = new DockerMgr();
        final CfgServer srv = new CfgServer();
        srv.setType(ServerType.DOCKER);
        srv.setSource(List.of(TEST_IMAGE_NO_HEALTHCHECK)); // has no healtchcheck
        srv.setHostname("idp5");
        srv.setStartupTimeoutSec(5); // to few seconds for startup
        long startms = System.currentTimeMillis();
        try {
            dmgr.startContainer(srv, cfg, null);
            assertThat(System.currentTimeMillis() - startms).isLessThan(20000);
        } finally {
            dmgr.stopContainer(srv);
        }
    }

    @Test
    public void testDockerMgrPauseUnpause() {
        // TODO ensure image with given version is available locally
        final DockerMgr dmgr = new DockerMgr();
        final CfgServer srv = new CfgServer();
        srv.setType(ServerType.DOCKER);
        srv.setSource(List.of(TEST_IMAGE)); // has no healtchcheck
        srv.setHostname("idp4");
        try {
            dmgr.startContainer(srv, cfg, null);
            dmgr.pauseContainer(srv);
            dmgr.unpauseContainer(srv);
        } finally {
            dmgr.stopContainer(srv);
        }
    }
}
