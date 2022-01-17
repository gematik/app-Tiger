package de.gematik.test.tiger.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import de.gematik.test.tiger.common.config.*;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Slf4j
public class TigerAdminUiController {

    @GetMapping("/")
    public String getStartPage() {
        return "adminui";
    }

    @GetMapping(value = "/openYamlFile", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Configuration openYamlFile(@RequestParam("cfgfile") String file) throws IOException {
        String yamlString = IOUtils.toString(new File(file).toURI(), StandardCharsets.UTF_8);
        var configurationLoader = new TigerConfigurationLoader();
        configurationLoader.readFromYaml(yamlString);
        return configurationLoader.instantiateConfigurationBean(Configuration.class, "tiger");
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

    @GetMapping(value = "/getConfigScheme", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public String getConfigScheme() {
        try {
            JacksonModule module = new JacksonModule();
            SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_7,
                OptionPreset.PLAIN_JSON
            ).with(module);

            configBuilder.forFields().withDefaultResolver(field -> {
                Class<?> declaringClass = field.getDeclaringType().getErasedType();
                if (!field.isFakeContainerItemScope()
                    && declaringClass.getName().startsWith("de.gematik.test")) {
                    MethodScope getter = field.findGetter();
                    if (getter != null) {
                        try {
                            return getter.getRawMember().invoke(declaringClass.getConstructor().newInstance());
                        } catch (Exception ex) {
                            throw new TigerConfigurationException("Unable to create instance for class " + declaringClass.getName(), ex);
                        }
                    }
                }
                return null;
            });

            SchemaGeneratorConfig config = configBuilder.build();
            SchemaGenerator generator = new SchemaGenerator(config);
            JsonNode jsonSchema = generator.generateSchema(CfgTemplate.class);
            return jsonSchema.toString();
        } catch (Exception e) {
            throw new TigerConfigurationException("Unable to read templates from classpath resources!", e);
        }
    }
}

