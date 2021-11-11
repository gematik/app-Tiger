package de.gematik.test.tiger.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.shaded.com.github.dockerjava.core.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.not;

@SpringBootTest
@AutoConfigureMockMvc
public class TigerAdminUiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testGetStartPage() throws Exception {
        this.mockMvc.perform(get("/start")).andExpect(status().isOk());
    }

    @Test
    void testGetYmlPage() throws Exception {
        this.mockMvc.perform(get("/yml-page")).andExpect(status().isOk());
    }

    @Test
    void testOpenYamlFile() throws Exception {
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
    void testSeeErrorMessageWhenOpenInvalidFile() throws Exception {
        String yamlContent = "tigerProxy:\n" +
                "\n" +
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
                .andExpect(content().string(containsString("Cannot deserialize value of type")))
                .andExpect(content().string(not(containsString("nested exception is"))))
                .andExpect(content().string(not(containsString("(through reference chain:"))));
    }
}
