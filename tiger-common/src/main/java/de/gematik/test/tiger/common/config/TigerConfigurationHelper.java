/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.parser.ParserException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This helper class helps to manage test suite configuration based on yaml config files.
 * <p>
 * First step is to use {@link #yamlToJson(String)} to create a JSON representation of the yaml config file. Now you can
 * optionally apply a template by calling {@link #applyTemplate(JSONObject, String, JSONArray, String)}. Then you can
 * overwrite yaml config values with env vars or system properties by calling {@link
 * #overwriteWithSysPropsAndEnvVars(String, String, JSONObject)}. Finally, you can convert to your data structure config
 * class by calling {@link #jsonStringToConfig(String, Class)}.
 * <p>
 * For simple test configurations without templating you can use the instance method {@link
 * #yamlReadOverwriteToConfig(String, String, Class)}. This method also performs the overwriting of yaml config values
 * with env vars and system properties. Due to Java Generics restrictions you will need to instantiate an instance to
 * use this method. XXXSERVERIDXXX is a placeholder here for the server id of the server you want to modify. With
 * version 0.15.0 of Tiger we switched from server list to server map. More details can be found in {@link
 * /doc/testenv-config-types.md}.
 *
 * <p>The format of the environment variables looks exemplaric like:
 * <ul>
 * <li>TIGER_TESTENV_TIGERPROXY_PROXYLOGLEVEL</li>
 * <li>TIGER_TESTENV_TIGERPROXY_PORT</li>
 * <li>TIGER_TESTENV_SERVERS_XXXSERVERID0XXX_TEMPLATE</li>
 * <li>TIGER_TESTENV_SERVERS_XXXSERVERID0XXX_STARTUPTIMEOUTSEC</li>
 * <li>...</li>
 * </ul></p>
 * <p>For <b>Envrionmental variables:</b><br/>TIGER is the product name passed in as parameter to {@link #overwriteWithSysPropsAndEnvVars(String, String, JSONObject)}
 * and then separated by "_" the hierarchy walking down all properties / path nodes being uppercase.
 * Entries in Lists are indexed by integer value.
 * <p>For <b>System properties:</b><br/>
 * <ul>
 *     <li>tiger.testenv.tigerProxy.proxyLogLevel</li>
 *     <li>tiger.testenv.tigerProxy.port</li>
 *     <li>tiger.testenv.tigerProxy.servers.XXXserveridXXX.template</li>
 *     <li>tiger.testenv.tigerProxy.servers.XXXserveridXXX.startupTimeoutSec</li>
 * </ul>
 * <p>
 * To use tokens such as ${TESTENV.xxxx} in the yaml file and replace it with appropriate values, first convert
 * the JSON Object to string and use the {@link de.gematik.test.tiger.common.TokenSubstituteHelper#substitute(String, String, Map)}
 * method to replace all tokens. Afterwards convert it back to JSONObject.
 *
 * @param <T>
 */
@Slf4j
@SuppressWarnings("unused")
public class TigerConfigurationHelper<T> {

    private static final ObjectMapper objMapper = new ObjectMapper();

    /**
     * A specialized {@link Constructor} that checks for duplicate keys.
     */
    private static class DuplicateMapKeysForbiddenConstructor extends SafeConstructor {

        @Override
        protected Map<Object, Object> constructMapping(MappingNode node) {
            try {
                List<String> keys = node.getValue().stream().map(v -> ((ScalarNode) v.getKeyNode()).getValue()).collect(
                    Collectors.toList());
                Set<String> duplicates = findDuplicates(keys);
                if (!duplicates.isEmpty()) {
                    throw new TigerConfigurationException(
                        "Duplicate keys in yaml file ('" + String.join(",", duplicates) + "')!");
                }
            } catch (Exception e) {
                throw new TigerConfigurationException("Duplicate keys in yaml file!", e);
            }
            try {
                return super.constructMapping(node);
            } catch (IllegalStateException e) {
                throw new ParserException("while parsing MappingNode",
                    node.getStartMark(), e.getMessage(), node.getEndMark());
            }
        }

        private <T> Set<T> findDuplicates(Collection<T> collection) {
            Set<T> uniques = new HashSet<>();
            return collection.stream()
                .filter(e -> !uniques.add(e))
                .collect(Collectors.toSet());
        }
    }

    /**
     * Old method, see {@link #yamlReadOverwriteToConfig(String, String, Class)}.
     *
     * @param yamlPath absolute path to yaml config file
     * @param product  name/id of product
     * @param cfgClazz class reference for the Configuration object to be created from config yaml file.
     * @return Configuration object
     * @deprecated
     */
    public T yamlToConfig(String yamlPath, String product, Class<T> cfgClazz) {
        return yamlReadOverwriteToConfig(yamlPath, product, cfgClazz);
    }

    /**
     * reads given yaml file to JSON object, applies env var / system property overwrite and returns the configuration
     * class's instance.
     *
     * @param yamlPath path to yaml config file
     * @param product  name/id of product
     * @param cfgClazz class reference for the Configuration object to be created from config yaml file.
     * @return Configuration object
     */
    public T yamlReadOverwriteToConfig(String yamlPath, String product, Class<T> cfgClazz) {
        try {
            JSONObject json = yamlToJson(yamlPath);
            overwriteWithSysPropsAndEnvVars(product.toUpperCase(), product, json);
            return objMapper.readValue(json.toString(), cfgClazz);
        } catch (JsonProcessingException e) {
            throw new TigerConfigurationException("Unable to convert YAML content to JSON!", e);
        }
    }

    /**
     * reads a given yaml file to JSON object.
     *
     * @param yamlPath path to yaml config file
     * @return json object
     */
    public static JSONObject yamlToJson(String yamlPath) {
        Yaml yaml = new Yaml(new DuplicateMapKeysForbiddenConstructor());
        try {
            Map<String, Object> map = yaml.load(IOUtils.toString(Path.of(yamlPath).toUri(), StandardCharsets.UTF_8));
            return new JSONObject(map);
        } catch (IOException e) {
            throw new TigerConfigurationException("Unable to read YAML file '" + yamlPath + "'!", e);
        }
    }

    /**
     * converts a given yaml content string to JSON object.
     *
     * @param yamlStr YAML content
     * @return JSON object representing the yaml content
     */
    public static JSONObject yamlStringToJson(String yamlStr) {
        Yaml yaml = new Yaml(new DuplicateMapKeysForbiddenConstructor());
        Map<String, Object> map = yaml.load(yamlStr);
        return new JSONObject(map);
    }

    public static JSONObject yamlConfigReadOverwriteToJson(String yamlBaseFilename, String product) {
        final File cfgFile = Path.of("config", product, yamlBaseFilename).toFile();
        final String yamlStr;
        if (cfgFile.canRead()) {

            try {
                yamlStr = IOUtils.toString(cfgFile.toURI(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new TigerConfigurationException("Failed to read YAML file " + cfgFile.getAbsolutePath() + "!", e);
            }
        } else {
            try (InputStream is = TigerConfigurationHelper.class.getResourceAsStream(
                "/config/" + product + "/" + yamlBaseFilename)) {
                assertThat(is)
                    .withFailMessage("Configuration file '" + cfgFile.getAbsolutePath()
                        + "' neither found in file system nor in classpath!")
                    .isNotNull();
                //noinspection ConstantConditions
                yamlStr = IOUtils.toString(is, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new TigerConfigurationException(
                    "Failed to read YAML from class path '/config/" + product + "/" + yamlBaseFilename + "' !", e);
            }
        }
        final JSONObject json = yamlStringToJson(yamlStr);
        overwriteWithSysPropsAndEnvVars(product.toUpperCase(), product, json);
        return json;
    }

    public T jsonToConfig(String jsonFile, Class<T> cfgClazz) {
        try {
            return objMapper.readValue(IOUtils.toString(Path.of(jsonFile).toUri(), StandardCharsets.UTF_8), cfgClazz);
        } catch (IOException e) {
            throw new TigerConfigurationException(
                "Failed to convert given JSON file '" + jsonFile +
                    "' to config object of class " + cfgClazz.getName() + "!", e);
        }
    }

    public T jsonStringToConfig(String jsonStr, Class<T> cfgClazz) {
        try {
            return objMapper.readValue(jsonStr, cfgClazz);
        } catch (JsonProcessingException e) {
            throw new TigerConfigurationException(
                "Failed to convert given JSON string to config object of class " + cfgClazz.getName() + "!", e);
        }
    }

    public static String toJson(Object cfg) {
        try {
            return objMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cfg);
        } catch (JsonProcessingException e) {
            throw new TigerConfigurationException("Failed to convert given object to JSON!", e);
        }
    }

    public static String toYaml(Object cfg) {
        Yaml yaml = new Yaml(new DuplicateMapKeysForbiddenConstructor());
        return yaml.dump(cfg);
    }


    public static void overwriteWithSysPropsAndEnvVars(String rootEnv, String rootProps, JSONObject json) {
        json.keys().forEachRemaining(key -> {
            Object obj = json.get(key);
            if (obj instanceof JSONObject) {
                overwriteWithSysPropsAndEnvVars(rootEnv + "_" + key.toUpperCase(), rootProps + "." + key,
                    (JSONObject) obj);
            } else if (obj instanceof JSONArray) {
                overwriteWithSysPropsAndEnvVars(rootEnv + "_" + key.toUpperCase(), rootProps + "." + key,
                    (JSONArray) obj);
            } else {
                log.info("checking for env " + rootEnv + "_" + key.toUpperCase() + ":" + obj);
                String value = System
                    .getProperty(rootProps + "." + key, System.getenv(rootEnv + "_" + key.toUpperCase()));
                if (value != null) {
                    log.info("modifying " + rootEnv + "_" + key.toUpperCase() + ":" + obj + " with " + value);
                    json.put(key, value);
                }
            }
        });
    }

    public static void applyTemplate(JSONArray cfgArray, String templateKey, JSONArray templates,
        String templateIdKey) {
        for (var i = 0; i < cfgArray.length(); i++) {
            var json = cfgArray.getJSONObject(i);
            if (json.has(templateKey)) {
                lookupAndApplyTemplate(json, templateKey, templates, templateIdKey);
            }
        }
    }

    public static void applyTemplate(JSONObject cfgMap, String templateKey, JSONArray templates,
        String templateIdKey) {
        for (String objectKey : cfgMap.keySet()) {
            var json = cfgMap.getJSONObject(objectKey);
            if (json.has(templateKey)) {
                lookupAndApplyTemplate(json, templateKey, templates, templateIdKey);
            }
        }
    }

    private static void lookupAndApplyTemplate(JSONObject json, String templateKey, JSONArray templates,
        String templateIdKey) {
        var templateId = json.getString(templateKey);
        boolean foundTemplate = false;
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
                foundTemplate = true;
            }
        }
        if (!foundTemplate) {
            throw new TigerConfigurationException("Unable to locate template '" + templateId + "'");
        }

    }

    private static void overwriteWithSysPropsAndEnvVars(String rootEnv, String rootProps, JSONArray jsonArray) {
        for (var i = 0; i < jsonArray.length(); i++) {
            Object obj = jsonArray.get(i);
            if (obj instanceof JSONObject) {
                overwriteWithSysPropsAndEnvVars(rootEnv + "_" + i, rootProps + "." + i, (JSONObject) obj);
            } else if (obj instanceof JSONArray) {
                overwriteWithSysPropsAndEnvVars(rootEnv + "_" + i, rootProps + "." + i, (JSONArray) obj);
            } else {
                log.info("checking for env " + rootEnv + "_" + i + ":" + obj);
                String value = System.getProperty(rootProps + "." + i, System.getenv(rootEnv + "_" + i));
                if (value != null) {
                    log.info("modifying " + rootEnv + "_" + i + ":" + obj + " with " + value);
                    jsonArray.put(i, value);
                }
            }
        }
    }
}
