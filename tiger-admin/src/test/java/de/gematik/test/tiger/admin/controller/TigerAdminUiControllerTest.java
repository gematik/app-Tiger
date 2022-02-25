/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.shaded.com.github.dockerjava.core.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
public class TigerAdminUiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testGetStartPage() throws Exception {
        this.mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    public void testOpenYamlFile() throws Exception {
        String relPath = Path.of("..", "tiger-testenv-mgr", "src", "test", "resources", "de", "gematik", "test",
            "tiger", "testenvmgr").toFile().toString();
        this.mockMvc.perform(get("/openYamlFile")
                .param("cfgfile", relPath + File.separator + "testAdminUI.yaml"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON.getMediaType()))
            .andExpect(jsonPath("$.tigerProxy.forwardToProxy.hostname").value(is("$SYSTEM")))
            .andExpect(jsonPath("$.servers.reverseproxy2.type").value(is("tigerProxy")))
            .andExpect(jsonPath("$.servers.testWinstone3.type").value(is("externalJar")));
    }

    @Test
    public void testSeeErrorMessageWhenOpenInvalidFile() throws Exception {

        String relPath = Path.of("..", "tiger-testenv-mgr", "src", "test", "resources", "de", "gematik", "test",
            "tiger", "testenvmgr").toFile().toString();
        this.mockMvc.perform(get("/openYamlFile")
                .param("cfgfile", relPath + File.separator + "testInvalidType.yaml"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.mainCause").value(containsString(
                "Unable to load testenv yaml file")))
            .andExpect(jsonPath("$.causes").value(hasSize(2)))
            .andExpect(jsonPath("$.causes[0]").value(containsString(
                "Error while reading configuration for class de.gematik.test.tiger.testenvmgr.config.Configuration with base-keys")))
            .andExpect(jsonPath("$.causes[1]").value(containsString(
                "not one of the values accepted for Enum class: [externalJar, compose, externalUrl, tigerProxy, docker]")));
    }

    @Test
    public void testGetServerTemplates() throws Exception {
        this.mockMvc.perform(MockMvcRequestBuilders.get("/getTemplates"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.templates").value(hasSize(9)))
            .andExpect(jsonPath("$.templates[0].templateName").value(is("idp-ref")))
            .andExpect(jsonPath("$.templates[8].templateName").value(is("pssim")));
    }
}
