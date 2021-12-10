/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

public class TestTigerConfiguration {

    @Test
    public void readTestEnvYaml() {
        assertThat(new TigerConfigurationHelper<TestCfg>().yamlReadOverwriteToConfig(
            "../tiger-testenv-mgr/src/main/resources/de/gematik/test/tiger/testenvmgr/templates.yaml",
            "tiger", TestCfg.class))
            .extracting(TestCfg::getTemplates)
            .asList()
            .extracting("templateName")
            .contains("idp-ref", "idp-rise-ru", "idp-rise-tu", "epa2", "epa2-fdv");
    }
}
