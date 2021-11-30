package de.gematik.test.tiger.admin.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
public class TigerFileNavigatorControllerTest {

    private static String slash = File.separator;
    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testMultipleConfigFiles() throws Exception {
        this.mockMvc.perform(MockMvcRequestBuilders.get(
                "/navigator/folder?current=../tiger-testenv-mgr/src/test/resources/de/gematik/test/tiger/testenvmgr"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.folders").value(hasSize(1)))
            .andExpect(jsonPath("$.cfgfiles").value(hasSize(16)))
            .andExpect(jsonPath("$.cfgfiles[0]").value(is("testAdminUI.yaml")))
            .andExpect(jsonPath("$.cfgfiles[15]").value(is("testUnknownTemplate.yaml")));
    }

    @Test
    public void testMultipleSubFolders() throws Exception {
        String relPath = Path.of("..", "tiger-testenv-mgr", "src", "test").toFile().toString();
        this.mockMvc.perform(MockMvcRequestBuilders.get("/navigator/folder?current=" + relPath))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.folders").value(hasSize(3)))
            .andExpect(jsonPath("$.cfgfiles").value(hasSize(0)))
            .andExpect(jsonPath("$.folders[0]").value(is("..")))
            .andExpect(jsonPath("$.folders[1]").value(is("java")))
            .andExpect(jsonPath("$.folders[2]").value(is("resources")))
            .andExpect(jsonPath("$.current").value(is(Path.of(new File(".").getAbsolutePath(), relPath).normalize().toString())));
    }
}
