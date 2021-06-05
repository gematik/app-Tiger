package de.gematik.test.tiger.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

@Slf4j
public class TigerConfigurationHelper<T> {

    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper objMapper = new ObjectMapper();

    @SneakyThrows
    public T yamlToConfig(String yamlFile, String product, Class<T> cfgClazz) {
        Object yamlCfg = yamlMapper.
            readValue(IOUtils.toString(Path.of(yamlFile).toUri(), StandardCharsets.UTF_8), Object.class);

        // convert to JSON and iterate over all entries looking for system properties or env vars to overwrite
        var json = new JSONObject(objMapper.writeValueAsString(yamlCfg));
        overwriteWithSysPropsAndEnvVars(product.toUpperCase(), product, json);

        return objMapper.readValue(json.toString(), cfgClazz);
    }

    @SneakyThrows
    public T jsonToConfig(String jsonFile, Class<T> cfgClazz) {
       return objMapper.readValue(IOUtils.toString(Path.of(jsonFile).toUri(), StandardCharsets.UTF_8), cfgClazz);
    }

    @SneakyThrows
    public static String toJson(Object cfg) {
        return objMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cfg);
    }

    @SneakyThrows
    public static String toYaml(Object cfg) {
        return yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cfg);
    }

    private void overwriteWithSysPropsAndEnvVars(String rootEnv, String rootProps, JSONObject json) {
        json.keySet().forEach(key -> {
            Object obj = json.get(key);
            if (obj instanceof JSONObject) {
                overwriteWithSysPropsAndEnvVars(rootEnv + "_" + key.toUpperCase(), rootProps + "." + key, (JSONObject)obj);
            } else if (obj instanceof JSONArray) {
                overwriteWithSysPropsAndEnvVars(rootEnv + "_" + key.toUpperCase(), rootProps + "." + key,  (JSONArray) obj);
            } else {
                log.info("checking for env " + rootEnv + "_" + key.toUpperCase()+ ":" + obj);
                String value = System.getProperty(rootProps + "." + key, System.getenv(rootEnv + "_" + key.toUpperCase()));
                if (value != null)  {
                    log.info("modifying " + rootEnv + "_" + key.toUpperCase()+ ":" + obj);
                    json.put(key, value);
                }
            }
        });
    }

    private void overwriteWithSysPropsAndEnvVars(String rootEnv, String rootProps, JSONArray jarr) {
        for (var i = 0; i < jarr.length(); i++) {
            Object obj = jarr.get(i);
            if (obj instanceof JSONObject) {
                overwriteWithSysPropsAndEnvVars(rootEnv + "_" + i, rootProps + "." + i,(JSONObject) obj);
            } else if (obj instanceof JSONArray) {
                overwriteWithSysPropsAndEnvVars(rootEnv + "_" + i, rootProps + "." + i, (JSONArray) obj);
            } else {
                log.info("checking for env " + rootEnv + "_" + i + ":" + obj);
                String value = System.getProperty(rootProps + "." + i, System.getenv(rootEnv + "_" + i));
                if (value != null)  {
                    log.info("modifying " + rootEnv + "_" + i + ":" + obj);
                    jarr.put(i, value);
                }
            }
        }
    }
}
