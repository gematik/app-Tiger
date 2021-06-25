/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import org.junit.Test;

public class TestTigerConfiguration {

    @Test
    public void readTestEnvYaml() {
        TestCfg cfg = new TigerConfigurationHelper<TestCfg>().yamlToConfig(
            "../tiger-testenv-mgr/src/main/resources/de/gematik/test/tiger/testenvmgr/templates.yaml",
            "tiger", TestCfg.class);
    }
}
