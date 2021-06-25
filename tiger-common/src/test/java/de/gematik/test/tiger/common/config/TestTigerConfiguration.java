/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestTigerConfiguration {

    @Test
    public void readTestEnvYaml() {
        assertThat(new TigerConfigurationHelper<TestCfg>().yamlToConfig(
                "../tiger-testenv-mgr/src/main/resources/de/gematik/test/tiger/testenvmgr/templates.yaml",
                "tiger", TestCfg.class))
                .extracting(TestCfg::getTemplates)
                .asList()
                .extracting("name")
                .contains("idp-ref", "idp-rise-rru", "idp-rise-rtu");
    }
}
