package de.gematik.test.tiger.admin.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerConfigurationHelper;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller
@Slf4j
public class TigerAdminUiController {

    @GetMapping("/")
    public String getStartPage() {
        return "adminui";
    }

    @GetMapping(value = "/openYamlFile", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public String openYamlFile(@RequestParam("cfgfile") String file) throws IOException {
        String yamlString = IOUtils.toString(new File(file).toURI(), StandardCharsets.UTF_8);
        JSONObject jsonCfg = TigerConfigurationHelper.yamlStringToJson(yamlString);
        Configuration configuration = new TigerConfigurationHelper<Configuration>()
            .jsonStringToConfig(jsonCfg.toString(), Configuration.class);
        return TigerConfigurationHelper.toJson(configuration);
    }

    @GetMapping(value = "/getTemplates", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public String getTemplates() {
        try {
            JSONObject jsonTemplate = TigerConfigurationHelper.yamlStringToJson(
                IOUtils.toString(Objects.requireNonNull(TigerTestEnvMgr.class.getResource(
                    "templates.yaml")).toURI(), StandardCharsets.UTF_8));
            return jsonTemplate.toString();
        } catch (Exception e) {
            throw new TigerConfigurationException("Unable to read templates from classpath resources!", e);
        }
    }
}

