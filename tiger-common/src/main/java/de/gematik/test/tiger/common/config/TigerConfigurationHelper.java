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

/**
 * This helper class eases test suite configuration based on yaml config files.
 * First step is to use {@link #yamlToJson(String)} to create a JSON representation of the yaml config file.
 * Now you can optionally apply a template by calling {@link #applyTemplate(JSONArray, String, JSONArray, String)}.
 * and overwrite yaml config values with env vars or system properties by calling
 * {@link #overwriteWithSysPropsAndEnvVars(String, String, JSONObject)}.
 *
 * For simple test configuration without templating you can use the instance method
 * {@link #yamlToConfig(String, String, Class)}.
 * Due to Java restrictions Generics can not be used with static methods so you will need to instantiate an instance
 * to use this method.
 *
 * @param <T>
 */
@Slf4j
public class TigerConfigurationHelper<T> {

    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper objMapper = new ObjectMapper();

    @SneakyThrows
    public T yamlToConfig(String yamlFile, String product, Class<T> cfgClazz) {
        JSONObject json = yamlToJson(yamlFile);
        overwriteWithSysPropsAndEnvVars(product.toUpperCase(), product, json);
        return objMapper.readValue(json.toString(), cfgClazz);
    }

    @SneakyThrows
    public static JSONObject yamlToJson(String yamlFile)  {
        Object yamlCfg = yamlMapper.
            readValue(IOUtils.toString(Path.of(yamlFile).toUri(), StandardCharsets.UTF_8), Object.class);
        return new JSONObject(objMapper.writeValueAsString(yamlCfg));
    }

    @SneakyThrows
    public static JSONObject yamlStringToJson(String yaml)  {
        Object yamlCfg = yamlMapper.readValue(yaml, Object.class);
        return new JSONObject(objMapper.writeValueAsString(yamlCfg));
    }
    @SneakyThrows
    public T jsonToConfig(String jsonFile, Class<T> cfgClazz) {
        return objMapper.readValue(IOUtils.toString(Path.of(jsonFile).toUri(), StandardCharsets.UTF_8), cfgClazz);
    }

    @SneakyThrows
    public T jsonStringToConfig(String jsonStr, Class<T> cfgClazz) {
        return objMapper.readValue(jsonStr, cfgClazz);
    }

    @SneakyThrows
    public static String toJson(Object cfg) {
        return objMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cfg);
    }

    @SneakyThrows
    public static String toYaml(Object cfg) {
        return yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cfg);
    }


    public static void overwriteWithSysPropsAndEnvVars(String rootEnv, String rootProps, JSONObject json) {
        json.keys().forEachRemaining(key -> {
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

    @SneakyThrows
    public static void applyTemplate(JSONArray cfgArray, String templateKey, JSONArray templates, String templateIdKey) {
        for (var i = 0; i < cfgArray.length(); i++) {
            var json = cfgArray.getJSONObject(i);
            var templateId = json.getString(templateKey);
            for (var j = 0; j < templates.length(); j++) {
                var jsonTemplate = templates.getJSONObject(j);
                if (jsonTemplate.getString(templateIdKey).equals(templateId)) {
                    jsonTemplate.keySet().stream()
                        .filter(key -> jsonTemplate.get(key) != null)
                        .filter(key -> jsonTemplate.get(key) instanceof JSONArray)
                        .filter(key -> !jsonTemplate.getJSONArray(key).isEmpty())
                        .filter(key -> !json.has(key) || json.get(key) == null || json.getJSONArray(key).isEmpty())
                        .forEach(key -> json.put(key, new JSONArray(jsonTemplate.getJSONArray(key))));
                    jsonTemplate.keySet().stream()
                        .filter(key -> jsonTemplate.get(key) != null)
                        .filter(key -> !(jsonTemplate.get(key) instanceof JSONArray))
                        .filter(key -> !json.has(key) || json.get(key) == null)
                        .forEach(key -> json.put(key, jsonTemplate.get(key)));
                }
            }
        }
    }

    private static void overwriteWithSysPropsAndEnvVars(String rootEnv, String rootProps, JSONArray jsonArray) {
        for (var i = 0; i < jsonArray.length(); i++) {
            Object obj = jsonArray.get(i);
            if (obj instanceof JSONObject) {
                overwriteWithSysPropsAndEnvVars(rootEnv + "_" + i, rootProps + "." + i,(JSONObject) obj);
            } else if (obj instanceof JSONArray) {
                overwriteWithSysPropsAndEnvVars(rootEnv + "_" + i, rootProps + "." + i, (JSONArray) obj);
            } else {
                log.info("checking for env " + rootEnv + "_" + i + ":" + obj);
                String value = System.getProperty(rootProps + "." + i, System.getenv(rootEnv + "_" + i));
                if (value != null)  {
                    log.info("modifying " + rootEnv + "_" + i + ":" + obj);
                    jsonArray.put(i, value);
                }
            }
        }
    }
}
