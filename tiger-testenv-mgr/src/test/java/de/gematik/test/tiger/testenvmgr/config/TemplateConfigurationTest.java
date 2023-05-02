/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.io.File;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

public class TemplateConfigurationTest {
    @Test
    public void testTemplateApplication() throws Exception {
        new EnvironmentVariables("TIGER_SERVERS_ERP_TEMPLATE", "erzpt-fd-ref")
            .execute(() -> {
                final String templatesYaml = FileUtils.readFileToString(
                    new File("src/main/resources/de/gematik/test/tiger/testenvmgr/templates.yaml"));

                TigerGlobalConfiguration.reset();
                TigerGlobalConfiguration.readTemplates(templatesYaml, "tiger.servers");
                TigerGlobalConfiguration.readFromYaml(
                    "servers:\n" +
                        "  idp:\n" +
                        "    template: idp-ref\n", "tiger");
                var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(Configuration.class, "tiger")
                    .get();
                assertThat(dummyBean.getServers().get("idp").getSource())
                    .containsExactly("eu.gcr.io/gematik-all-infra-prod/idp/idp-server");
                assertThat(dummyBean.getServers().get("erp").getSource())
                    .containsExactly("eu.gcr.io/gematik-all-infra-prod/erezept/ref-erx-fd-server");
            });
    }

    @SneakyThrows
    @Test
    public void readTigerYamlWithLogFile() {
        TigerGlobalConfiguration.readFromYaml(
            "tiger:\n"
                + "  servers:\n"
                + "    testExternalJar:\n"
                + "      type: externalJar\n"
                + "      source:\n"
                + "        - http://localhost:${mockserver.port}/download\n"
                + "      healthcheckUrl: http://127.0.0.1:${free.port.0}\n"
                + "      logFile: 'target/serverLogs/Test.log'\n"
                + "      externalJarOptions:\n"
                + "        workingDir: 'target/'\n"
                + "        arguments:\n"
                + "          - --httpPort=${free.port.0}\n"
                + "          - --webroot=.\n");
        var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(Configuration.class, "tiger")
            .get();
        assertThat(dummyBean.getServers().get("testExternalJar").getLogFile())
            .contains("target/serverLogs/Test.log");
    }

    @SneakyThrows
    @Test
    public void readTigerYamlWithLogDirectory() {
        TigerGlobalConfiguration.readFromYaml(
            FileUtils.readFileToString(
                new File("src/test/resources/testExternalJarDir.yaml"), StandardCharsets.UTF_8));
        var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(Configuration.class, "tiger")
            .get();
        assertThat(dummyBean.getServers().get("testExternalJar").getLogFile())
            .contains("target/serverLogs");
    }
}
