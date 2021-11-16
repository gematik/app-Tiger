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
import org.springframework.mock.web.MockMultipartFile;
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
        String relPath = Path.of("..", "tiger-testenv-mgr", "src", "test", "resources", "de", "gematik", "test", "tiger", "testenvmgr").toFile().toString();
        this.mockMvc.perform(get("/openYamlFile")
                        .param("cfgfile",   relPath + File.separator + "testAdminUI.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON.getMediaType()))
            .andExpect(jsonPath("$.reverseproxy2.type").value(is("tigerProxy")))
            .andExpect(jsonPath("$.testWinstone3.type").value(is("externalJar")));
    }

    @Test
    public void testSeeErrorMessageWhenOpenInvalidFile() throws Exception {

        String relPath = Path.of("..", "tiger-testenv-mgr", "src", "test", "resources", "de", "gematik", "test", "tiger", "testenvmgr").toFile().toString();
        this.mockMvc.perform(get("/openYamlFile")
            .param("cfgfile",   relPath + File.separator + "testInvalidType.yaml"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.mainCause").value(containsString("Failed to convert given JSON string to config object of class de.gematik.test.tiger.testenvmgr.config.Configuration!")))
            .andExpect(jsonPath("$.causes").value(hasSize(1)))
            .andExpect(jsonPath("$.causes[0]").value(containsString("Cannot deserialize value of type `de.gematik.test.tiger.common.config.ServerType` from String \"NOTEXISTING\": not one of the values accepted for Enum class: [externalJar, compose, externalUrl, tigerProxy, docker]\n"
                + " at [Source: (String)\"{\"servers\":{\"testInvalidType\":{\"template\":\"idp-ref\",\"active\":true,\"hostname\":\"invalid\","
                + "\"source\":[\"https://idp-test.zentral.idp.splitdns.ti-dienste.de/\"],\"type\":\"NOTEXISTING\"}}}\"; line: 1, column: 161] "
                + "(through reference chain: de.gematik.test.tiger.testenvmgr.config.Configuration[\"servers\"]->"
                + "java.util.LinkedHashMap[\"testInvalidType\"]->de.gematik.test.tiger.testenvmgr.config.CfgServer[\"type\"])")));
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
