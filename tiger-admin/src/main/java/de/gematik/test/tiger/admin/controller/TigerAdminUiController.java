package de.gematik.test.tiger.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerConfigurationHelper;
import de.gematik.test.tiger.common.config.TigerConfigurationLoader;
import de.gematik.test.tiger.common.data.config.CfgTemplate;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

@Controller
@Slf4j
public class TigerAdminUiController {


    static String templatesYaml;

    @GetMapping("/")
    public String getStartPage() {
        return "adminui";
    }

    @GetMapping(value = "/tiger_user_manual.html", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String getManualPage() {
        try {
            return IOUtils.resourceToString("/static/manual/tiger_user_manual.html", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new TigerConfigurationException("Unable to read user manual from classpath resources!", e);
        }
    }


    @GetMapping(value = "media/{mediafile}.svg", produces = "image/svg+xml")
    public ResponseEntity<byte[]> getManualPageMediaSVG(@PathVariable("mediafile") String mediaFile)
        throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/static/manual/media/" + mediaFile + ".svg")) {
            if (is == null) {
                throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "media file " + mediaFile + " not found"
                );
            }
            return new ResponseEntity<>(IOUtils.toByteArray(is), HttpStatus.OK);
        }
    }


    @GetMapping(value = "{svgfile}.svg", produces = "image/svg+xml")
    public ResponseEntity<byte[]> getManualPageSVG(@PathVariable("svgfile") String mediaFile) throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/static/manual/" + mediaFile + ".svg")) {
            if (is == null) {
                throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "media file " + mediaFile + " not found"
                );
            }
            return new ResponseEntity<>(IOUtils.toByteArray(is), HttpStatus.OK);
        }
    }

    @GetMapping(value = "/media/{mediafile}.png", produces = "image/png")
    public ResponseEntity<byte[]> getManualPageMediaPNG(@PathVariable("mediafile") String mediaFile)
        throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/static/manual/media/" + mediaFile + ".png")) {
            if (is == null) {
                throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "media file " + mediaFile + " not found"
                );
            }
            return new ResponseEntity<>(IOUtils.toByteArray(is), HttpStatus.OK);
        }
    }

    @GetMapping(value = "/examples/{examplefile}")
    public ResponseEntity<byte[]> getManualPageExampleFile(@PathVariable("examplefile") String exampleFile)
        throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/static/manual/examples/" + exampleFile)) {
            if (is == null) {
                throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Example file " + exampleFile + " not found"
                );
            }
            return new ResponseEntity<>(IOUtils.toByteArray(is), HttpStatus.OK);
        }
    }

    @GetMapping(value = "/openYamlFile", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Configuration openYamlFile(@RequestParam("cfgfile") String file) throws IOException {
        loadTemplates();
        String yamlString = IOUtils.toString(new File(file).toURI(), StandardCharsets.UTF_8);
        var configurationLoader = new TigerConfigurationLoader();
        configurationLoader.readTemplates(templatesYaml, "tiger", "servers");
        configurationLoader.readFromYaml(yamlString, "tiger");
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
                            throw new TigerConfigurationException(
                                "Unable to create instance for class " + declaringClass.getName(), ex);
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


    @PostMapping(value = "/saveYamlFile", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public String saveYamlFile(@RequestBody String jsonStr) {
        JSONObject json = new JSONObject(jsonStr);
        File cfgFile = new File(json.getString("folder") + File.separatorChar + json.getString("file"));
        try {
            DumperOptions options = new DumperOptions();
            options.setIndent(2);
            options.setPrettyFlow(true);
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            Yaml yaml = new Yaml(options);
            FileUtils.writeStringToFile(cfgFile, yaml.dump(yaml.load(json.getJSONObject("config").toString(4))),
                StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new TigerConfigurationException("Unable to save configuration to file " + cfgFile.getAbsolutePath());
        }
        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put("state", "OK");
        return jsonResponse.toString();
    }

    private void loadTemplates() {
        if (templatesYaml != null) {
            return;
        }
        final URL templatesUrl = TigerTestEnvMgr.class.getResource("templates.yaml");
        try {
            templatesYaml = IOUtils.toString(
                Objects.requireNonNull(templatesUrl).toURI(),
                StandardCharsets.UTF_8);
        } catch (IOException | URISyntaxException e) {
            throw new TigerConfigurationException("Unable to initialize templates!", e);
        }
    }
}

