package de.gematik.test.tiger.testenvmgr.config;

import de.gematik.test.tiger.testenvmgr.DockerMgr;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;

public class TestDockerMgr {

    //@Test
    public void testDockerMgr() {
        final DockerMgr dmgr = new DockerMgr();
        final CfgServer srv = new CfgServer();
        srv.setInstanceUri("docker:gstopdr1.top.local/idp/idp-server:12.0.0-680");
        srv.setName("idp");
        srv.setProduct(CfgProductType.IDP_REF);
        dmgr.startContainer(srv);
        dmgr.stopContainer(srv);
    }
}
