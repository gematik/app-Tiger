package de.gematik.test.tiger.testenvmgr.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.gematik.test.tiger.testenvmgr.DockerMgr;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;

public class TestDockerMgr {

    @Test
    public void testDockerMgr() {
        final DockerMgr dmgr = new DockerMgr();
        final CfgServer srv = new CfgServer();
        srv.setInstanceUri("docker:gstopdr1.top.local/idp/idp-server:15.0.0-759");
        srv.setName("idp");
        srv.setProduct(CfgProductType.IDP_REF);
        dmgr.startContainer(srv);
        dmgr.stopContainer(srv);
    }

    @Test
    public void testDockerMgrStartupTimeoutFallback() {
        // TODO ensure image with given version is available locally
        final DockerMgr dmgr = new DockerMgr();
        final CfgServer srv = new CfgServer();
        srv.setInstanceUri("docker:gstopdr1.top.local/idp/idp-server:15.0.0-108"); // has no healtchcheck
        srv.setName("idp");
        srv.setStartupTimeoutSec(5); // to few seconds for startup
        srv.setProduct(CfgProductType.IDP_REF);
        long startms = System.currentTimeMillis();
        dmgr.startContainer(srv);
        assertThat(System.currentTimeMillis() - startms).isLessThan(14000);
        // 9s to get docker up and running and starting container and check no health working
        dmgr.stopContainer(srv);
    }
}
