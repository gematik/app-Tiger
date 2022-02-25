/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;

public class TemplateConfigurationTest {
    @Test
    public void testTemplateApplication() throws Exception {
        withEnvironmentVariable("TIGER_SERVERS_ERP_TEMPLATE", "erzpt-fd-ref")
            .execute(() -> {
                final String templatesYaml = FileUtils.readFileToString(
                    new File("src/main/resources/de/gematik/test/tiger/testenvmgr/templates.yaml"));

                TigerGlobalConfiguration.reset();
                TigerGlobalConfiguration.readTemplates(templatesYaml, "tiger.servers");
                TigerGlobalConfiguration.readFromYaml(
                    "servers:\n" +
                        "  idp:\n" +
                        "    template: idp-ref\n", "tiger");
                var dummyBean = TigerGlobalConfiguration.instantiateConfigurationBean(Configuration.class, "tiger");
                assertThat(dummyBean.getServers().get("idp").getSource())
                    .containsExactly("gstopdr1.top.local/idp/idp-server");
                assertThat(dummyBean.getServers().get("erp").getSource())
                    .containsExactly("gstopdr1.top.local/erezept/ref-erx-fd-server");
            });
    }
}
