package de.gematik.test.tiger.admin.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
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
        //language=yaml
        String yamlContent = "tigerProxy:\n" +
                "  forwardToProxy:\n" +
                "    hostname: $SYSTEM\n" +
                "\n" +
                "servers:\n" +
                "  testExternalJar:\n" +
                "    hostname: testExternalJar\n" +
                "    type: externalJar\n" +
                "    source:\n" +
                "      - https://sourceforge.net/projects/winstone/files/winstone/v0.9.10/winstone-0.9.10.jar/download\n" +
                "    externalJarOptions:\n" +
                "      healthcheck: http://127.0.0.1:9107\n";

        MockMultipartFile yamlFile = new MockMultipartFile("fileName", "fileName.yaml", "application/json", yamlContent.getBytes());

        this.mockMvc.perform(MockMvcRequestBuilders.multipart("/openYamlFile")
                        .file(yamlFile)
                        .param("fileName", "fileName"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON.getMediaType()));
    }

    @Test
    public void testSeeErrorMessageWhenOpenInvalidFile() throws Exception {
        //language=yaml
        String yamlContent =
            "servers:\n" +
                "  testInvalidType:\n" +
                "    hostname: invalid\n" +
                "    template: idp-ref\n" +
                "    type: NOTEXISTING\n" +
                "    source:\n" +
                "      - https://idp-test.zentral.idp.splitdns.ti-dienste.de/\n" +
                "    active: true";

        MockMultipartFile yamlFile = new MockMultipartFile("fileName", "fileName.yaml", "application/json", yamlContent.getBytes());

        this.mockMvc.perform(MockMvcRequestBuilders.multipart("/openYamlFile")
                        .file(yamlFile)
                        .param("fileName", "fileName"))
                .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.mainCause").value(containsString("Failed to convert given JSON string to config object of class de.gematik.test.tiger.testenvmgr.config.Configuration!")))
            .andExpect(jsonPath("$.causes").value(hasSize(1)))
            .andExpect(jsonPath("$.causes[0]").value(containsString("Cannot deserialize value of type `de.gematik.test.tiger.common.config.ServerType` from String \"NOTEXISTING\": not one of the values accepted for Enum class: [externalJar, compose, externalUrl, tigerProxy, docker]\n"
                + " at [Source: (String)\"{\"servers\":{\"testInvalidType\":{\"template\":\"idp-ref\",\"active\":true,\"hostname\":\"invalid\",\"source\":[\"https://idp-test.zentral.idp.splitdns.ti-dienste.de/\"],\"type\":\"NOTEXISTING\"}}}\"; line: 1, column: 161] (through reference chain: de.gematik.test.tiger.testenvmgr.config.Configuration[\"servers\"]->java.util.LinkedHashMap[\"testInvalidType\"]->de.gematik.test.tiger.testenvmgr.config.CfgServer[\"type\"])")))
        ;

    }
}
