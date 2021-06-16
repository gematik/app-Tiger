/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.gematik.test.tiger.testenvmgr.DockerMgr;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvException;
import java.util.List;
import org.junit.Test;

public class TestDockerMgr {

    Configuration cfg = new Configuration();

    @Test
    public void testDockerMgr() {
        final DockerMgr dmgr = new DockerMgr();
        final CfgServer srv = new CfgServer();
        srv.setType("docker");
        srv.setSource(List.of("gstopdr1.top.local/idp/idp-server:17.0.0-38"));
        srv.setName("idp");
        srv.setProduct(CfgProductType.IDP_REF);
        dmgr.startContainer(srv, cfg, null);
        dmgr.stopContainer(srv);
    }

    @Test
    public void testDockerMgrStartupTimeoutFallback() {
        // TODO ensure image with given version is available locally
        final DockerMgr dmgr = new DockerMgr();
        final CfgServer srv = new CfgServer();
        dmgr.pullImage("gstopdr1.top.local/idp/idp-server:17.0.0-38");
        srv.setType("docker");
        srv.setSource(List.of("gstopdr1.top.local/idp/idp-server:17.0.0-38")); // has no healtchcheck
        srv.setName("idp");
        srv.setStartupTimeoutSec(5); // to few seconds for startup
        srv.setProduct(CfgProductType.IDP_REF);
        long startms = System.currentTimeMillis();
        assertThatThrownBy(() -> { dmgr.startContainer(srv, cfg,null); }).isInstanceOf(TigerTestEnvException.class);
        assertThat(System.currentTimeMillis() - startms).isLessThan(60000);
        // 9s to get docker up and running and starting container and check no health working
        // docker host environment -> Time elapsed: 26.062 sec
        dmgr.stopContainer(srv);
    }

    @Test
    public void testDockerMgrPauseUnpause() {
        // TODO ensure image with given version is available locally
        final DockerMgr dmgr = new DockerMgr();
        final CfgServer srv = new CfgServer();
        dmgr.pullImage("gstopdr1.top.local/idp/idp-server:17.0.0-38");
        srv.setType("docker");
        srv.setSource(List.of("gstopdr1.top.local/idp/idp-server:17.0.0-38")); // has no healtchcheck
        srv.setName("idp");
        srv.setProduct(CfgProductType.IDP_REF);
        try {
            dmgr.startContainer(srv, cfg,null);
            dmgr.pauseContainer(srv);
            dmgr.unpauseContainer(srv);
        } finally {
            dmgr.stopContainer(srv);
        }
    }
}
