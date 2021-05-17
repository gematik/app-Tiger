package de.gematik.test.tiger.testenvmgr.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import java.io.File;
import org.junit.Test;

public class TestTigerTestEnvMgr {

    @Test
    public void testReadConfig() {
        final Configuration cfg = new Configuration();
        cfg.readConfig(new File("src/test/resources/de/gematik/test/tiger/testenvmgr/idpOnly.yaml").toURI());
        assertThat(cfg.getServers()).hasSize(4);
        assertThat(cfg.getServers().get(0).getParams()).isEmpty();
        assertThat(cfg.getServers().get(2).getParams()).isEmpty();
    }

    @Test
    public void testReadTemplates() {
        final Configuration cfg = new Configuration();
        cfg.readConfig(new File("src/main/resources/de/gematik/test/tiger/testenvmgr/templates.yaml").toURI());
        assertThat(cfg.getTemplates()).hasSize(4);
        assertThat(cfg.getTemplates().get(0).getPkiKeys()).hasSize(3);
    }

    @Test
    public void testCreateShutdownEnv() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/idpOnly.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.setUpEnvironment();
        CfgServer srv = new CfgServer();
        srv.setName("idp");
        srv.setInstanceUri("docker:anything......");
        envMgr.shutDown(srv);
    }

    @Test
    public void testCreateExternalEnv() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/riseIdpOnly.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.setUpEnvironment();
    }


    @Test
    public void testCreateInvalidInstanceType() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/invalidInstanceType.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerTestEnvException.class);
    }

    @Test
    public void testCreateNonExisitngVersion() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/idpNonExisitngVersion.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerTestEnvException.class);
    }

    // TODO check pkis set, routings set,....

}
