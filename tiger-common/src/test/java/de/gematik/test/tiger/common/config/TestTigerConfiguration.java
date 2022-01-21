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
