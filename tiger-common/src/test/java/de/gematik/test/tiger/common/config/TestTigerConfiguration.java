/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class TestTigerConfiguration {

    @SneakyThrows
    @Test
    public void readWithTigerConfiguration() {
        TigerGlobalConfiguration.readFromYaml(
            FileUtils.readFileToString(new File("../tiger-testenv-mgr/src/main/resources/de/gematik/test/tiger/testenvmgr/templates.yaml")), "tiger");
        assertThat(TigerGlobalConfiguration.instantiateConfigurationBean(TestCfg.class, "tiger"))
            .extracting(TestCfg::getTemplates)
            .asList()
            .extracting("templateName")
            .contains("idp-ref", "idp-rise-ru", "idp-rise-tu", "epa2", "epa2-fdv");
    }
}
