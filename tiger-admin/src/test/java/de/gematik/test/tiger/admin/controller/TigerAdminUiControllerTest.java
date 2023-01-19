/*
 * Copyright (c) 2023 gematik GmbH
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

package de.gematik.test.tiger.admin.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class TigerAdminUiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testGetStartPage() throws Exception {
        this.mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void testOpenYamlFile() throws Exception {
        String relPath = Path.of("..", "tiger-testenv-mgr", "src", "test", "resources", "de", "gematik", "test",
            "tiger", "testenvmgr").toFile().toString();
        this.mockMvc.perform(get("/openYamlFile")
                .param("cfgfile", relPath + File.separator + "testAdminUI.yaml"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.tigerProxy.forwardToProxy.hostname").value(is("$SYSTEM")))
            .andExpect(jsonPath("$.servers.reverseproxy2.type").value(is("tigerProxy")))
            .andExpect(jsonPath("$.servers.testWinstone3.type").value(is("externalJar")));
    }

    @Test
    void testSeeErrorMessageWhenOpenInvalidFile() throws Exception {

        String relPath = Path.of("..", "tiger-testenv-mgr", "src", "test", "resources", "de", "gematik", "test",
            "tiger", "testenvmgr").toFile().toString();
        this.mockMvc.perform(get("/openYamlFile")
                .param("cfgfile", relPath + File.separator + "testInvalidYaml.yaml"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.mainCause").value(containsString(
                "Unable to load testenv yaml file")))
            .andExpect(jsonPath("$.causes").value(hasSize(2)))
            .andExpect(jsonPath("$.causes[0]").value(containsString(
                "Error while reading configuration")));
    }

    @Test
    void testGetServerTemplates() throws Exception {
        this.mockMvc.perform(MockMvcRequestBuilders.get("/getTemplates"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.templates").value(hasSize(9)))
            .andExpect(jsonPath("$.templates[0].templateName").value(is("idp-ref")))
            .andExpect(jsonPath("$.templates[8].templateName").value(is("pssim")));
    }
}
