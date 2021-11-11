package de.gematik.test.tiger.admin.controller;

import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerConfigurationHelper;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Controller
@Slf4j
public class TigerAdminUiController {

    @GetMapping("/")
    public String getStartPage() {
        return "ymlPage";
    }

    @PostMapping(value = "/openYamlFile", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public String openYamlFile(@RequestParam("fileName") MultipartFile file) throws IOException {
        ByteArrayInputStream stream = new ByteArrayInputStream(file.getBytes());
        String yamlString = IOUtils.toString(stream, StandardCharsets.UTF_8);

        JSONObject jsonCfg = TigerConfigurationHelper.yamlStringToJson(yamlString);

        Configuration configuration = new TigerConfigurationHelper<Configuration>()
            .jsonStringToConfig(jsonCfg.toString(), Configuration.class);

        return TigerConfigurationHelper.toJson(configuration.getServers());
    }
}

