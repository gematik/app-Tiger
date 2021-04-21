package de.gematik.test.tiger.testenvmgr.config;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import java.io.File;
import org.junit.Test;

public class TestTigerTestEnvMgr {

    @Test
    public void testReadConfig() {
        final Configuration cfg = new Configuration();
        cfg.readConfig(new File("tiger-testenv.yaml").toURI());
        assertThat(cfg.getServers()).hasSize(3);
        assertThat(cfg.getServers().get(0).getParams()).hasSize(2);
        assertThat(cfg.getServers().get(2).getParams()).hasSize(0);
    }

    @Test
    public void testCreateEnv() {
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.setUpEnvironment();
    }
}
