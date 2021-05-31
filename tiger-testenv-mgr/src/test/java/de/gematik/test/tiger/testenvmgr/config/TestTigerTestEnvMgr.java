/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import java.io.File;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

public class TestTigerTestEnvMgr {

    @Test
    public void testReadConfig() {
        final Configuration cfg = new Configuration();
        cfg.readConfig(new File("src/test/resources/de/gematik/test/tiger/testenvmgr/idpOnly.yaml").toURI());
        assertThat(cfg.getServers()).hasSize(4);
    }

    @Test
    public void testReadTemplates() {
        final Configuration cfg = new Configuration();
        cfg.readConfig(new File("src/main/resources/de/gematik/test/tiger/testenvmgr/templates.yaml").toURI());
        assertThat(cfg.getTemplates()).hasSize(6);
        assertThat(cfg.getTemplates().get(0).getPkiKeys()).hasSize(3);
    }

    @Test
    public void testCreateShutdownEnv() {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/idpOnly.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.setUpEnvironment();
        CfgServer srv = new CfgServer();
        srv.setName("idp");
        srv.setType("docker");
        srv.setSource(List.of("anything......"));
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
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/idpNonExistingVersion.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        assertThatThrownBy(envMgr::setUpEnvironment).isInstanceOf(TigerTestEnvException.class);
    }


    @Test
    public void load_yml_as_map_snake() {

        String yamlAsString = "{JYaml: { test1: Original Java Implementation}, "
            + "JvYaml: Java port of RbYaml, SnakeYAML: Java 5 / YAML 1.1, "
            + "YamlBeans: To/from JavaBeans}";

        Yaml yaml = new Yaml();

        @SuppressWarnings("unchecked")
        Map<String, String> yamlParsers = (Map<String, String>) yaml
            .load(yamlAsString);

        assertThat(yamlParsers.keySet()).contains("JYaml", "JvYaml", "YamlBeans", "SnakeYAML");
    }

    //@Test
    public void testCreateEpa2() throws InterruptedException {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/epa.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.setUpEnvironment();
        Thread.sleep(200000);
    }

    @Test
    public void testCreateEpa2FDV() throws InterruptedException {
        System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/de/gematik/test/tiger/testenvmgr/epa-fdv.yaml");
        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        envMgr.setUpEnvironment();
        Thread.sleep(2000);
    }

    // TODO check pkis set, routings set,....

}
