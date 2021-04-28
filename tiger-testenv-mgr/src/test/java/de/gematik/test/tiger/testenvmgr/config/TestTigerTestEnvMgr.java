package de.gematik.test.tiger.testenvmgr.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        System.setProperty("TIGER_TESTENV_CFGFILE", "../tiger-test-lib/src/test/resources/testdata/idpAnderezept.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        // TODO for now 210428 this doesnt work as tiger proxy does not do https forwards so check it throws a failure
        assertThatThrownBy(() -> envMgr.setUpEnvironment()).hasMessage("Startup of server erzpt-default timed out after 40 seconds!");
    }
}
