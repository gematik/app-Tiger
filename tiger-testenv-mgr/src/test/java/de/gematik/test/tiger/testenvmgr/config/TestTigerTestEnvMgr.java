package de.gematik.test.tiger.testenvmgr.config;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import java.io.File;
import lombok.SneakyThrows;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;

public class TestTigerTestEnvMgr {

    @Test
    public void testReadConfig() {
        final Configuration cfg = new Configuration();
        cfg.readConfig(new File("../tiger-testenv.yaml").toURI());
        assertThat(cfg.getServers()).hasSize(4);
        assertThat(cfg.getServers().get(0).getParams()).isEmpty();
        assertThat(cfg.getServers().get(2).getParams()).isEmpty();
    }

    @SneakyThrows
    @Test
    public void testCreateEnv() {
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        // TODO not for now
        envMgr.setUpEnvironment();
        Thread.sleep(150000);
    }
}
