/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
