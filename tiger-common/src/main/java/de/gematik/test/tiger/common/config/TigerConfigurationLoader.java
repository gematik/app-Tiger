/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.common.config;

import static de.gematik.test.tiger.common.config.TigerConfigurationKeyString.wrapAsKey;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.common.data.config.ConfigurationFileType;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.yaml.snakeyaml.Yaml;

@Slf4j
public class TigerConfigurationLoader {

  public static final String TIGER_CONFIGURATION_ATTRIBUTE_KEY = "tigerConfiguration";
  private final TigerConfigurationSourcesManager sourcesManager =
      new TigerConfigurationSourcesManager();
  @Getter private ObjectMapper objectMapper;

  public TigerConfigurationLoader() {
    initialize();
  }

  private static boolean parseBoolean(String rawValue) {
    return "1".equals(rawValue) || Boolean.parseBoolean(rawValue);
  }

  private static String mapConflictResolver(String e1, String e2, String propertySourceName) {
    if (e1.equals(e2)) {
      return e1;
    } else {
      throw new TigerConfigurationException(
          "Found two conflicting "
              + propertySourceName
              + " with values '"
              + e1
              + "' and '"
              + e2
              + "'. Resolve this conflict manually!");
    }
  }

  public static Map<TigerConfigurationKey, String> addConfigurationFileToMap(
      final Object value,
      final TigerConfigurationKey baseKeys,
      final Map<TigerConfigurationKey, String> valueMap) {
    if (value instanceof Map<?, ?> asMap) {
      asMap.forEach(
          (key, value1) -> {
            var newList = new TigerConfigurationKey(baseKeys);
            newList.add((String) key);
            addConfigurationFileToMap(value1, newList, valueMap);
          });
    } else if (value instanceof List<?> asList) {
      int counter = 0;
      for (Object entry : asList) {
        TigerConfigurationKey newList = new TigerConfigurationKey(baseKeys);
        newList.add(wrapAsKey(Integer.toString(counter++)));
        addConfigurationFileToMap(entry, newList, valueMap);
      }
    } else {
      if (value != null) {
        valueMap.put(baseKeys, value.toString());
      }
    }
    return valueMap;
  }

  public void reset() {
    sourcesManager.reset();
    initializeObjectMapper();
  }

  public void initialize() {
    if (objectMapper == null) {
      initializeObjectMapper();
    }
  }

  private void initializeObjectMapper() {
    val builder = new TigerConfigurationObjectMapperBuilder(this);
    this.objectMapper = builder.retrieveLenientObjectMapper();
  }

  public String readString(String key) {
    return readStringOptional(key)
        .orElseThrow(
            () -> new TigerConfigurationException("Could not find value for '" + key + "'"));
  }

  public String readString(String key, String defaultValue) {
    return readStringOptional(key).orElse(defaultValue);
  }

  public Optional<String> readStringOptional(String key) {
    TigerConfigurationKey splittedKey =
        new TigerConfigurationKey(TokenSubstituteHelper.substitute(key, this));
    return readStringOptional(splittedKey);
  }

  public Optional<String> readStringOptional(TigerConfigurationKey key) {
    return sourcesManager
        .getSortedStream()
        .filter(source -> source.containsKey(key))
        .map(source -> source.getValue(key))
        .findFirst();
  }

  /**
   * Instantiates a bean of the given class. The base-keys denote the point from which the keys are
   * taken. If values can not be substituted (e.g. ${key.that.does.not.exist}) they are simply
   * returned as a string. This behaviour is more relaxed towards input errors but might delay
   * failures from startup to runtime.
   *
   * <p>If the base-keys lead to a non-defined (i.e. empty) node in the tree (no values have been
   * read) an empty optional is returned.
   *
   * @param configurationBeanClass The class of the configuration bean
   * @param baseKeys Where in the configuration tree should the values be taken from?
   * @return An instance of configurationBeanClass filled with values taken from the configuration
   *     tree
   */
  public <T> Optional<T> instantiateConfigurationBean(
      Class<T> configurationBeanClass, String... baseKeys) {
    return instantiateConfigurationBean(configurationBeanClass, objectMapper, baseKeys);
  }

  @SneakyThrows
  private <T> Optional<T> instantiateConfigurationBean(
      Class<T> configurationBeanClass, ObjectMapper objectMapper, String... baseKeys) {
    initialize();

    TreeNode targetTree = convertToTreeUnresolved();
    final TigerConfigurationKey configurationKey = new TigerConfigurationKey(baseKeys);
    for (TigerConfigurationKeyString key : configurationKey) {
      if (targetTree.get(key.getValue()) == null) {
        return Optional.empty();
      }
      targetTree = targetTree.get(key.getValue());
    }
    try {
      return Optional.of(objectMapper.treeToValue(targetTree, configurationBeanClass));
    } catch (JacksonException e) {
      log.debug(
          "Error while converting the following tree: {}",
          objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(targetTree));
      Throwable ex = e;
      while (ex.getCause() != null) {
        ex = ex.getCause();
      }
      throw new TigerConfigurationException(
          "Error while reading configuration for class "
              + configurationBeanClass.getName()
              + " with base-keys "
              + Arrays.toString(baseKeys)
              + " and root cause '"
              + ex.getMessage()
              + "'",
          e);
    }
  }

  @SneakyThrows
  public <T> T instantiateConfigurationBean(
      TypeReference<T> configurationBeanType, String... baseKeys) {
    initialize();

    TreeNode targetTree = convertToTreeUnresolved();
    final TigerConfigurationKey configurationKey = new TigerConfigurationKey(baseKeys);
    for (TigerConfigurationKeyString key : configurationKey) {
      if (targetTree.get(key.getValue()) == null) {
        return objectMapper.readValue("[]", configurationBeanType);
      }
      targetTree = targetTree.get(key.getValue());
    }
    try (JsonParser jsonParser = objectMapper.treeAsTokens(targetTree)) {
      return jsonParser.readValueAs(configurationBeanType);
    } catch (JacksonException e) {
      log.debug(
          "Error while converting the following tree: {}",
          objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(targetTree));
      throw new TigerConfigurationException(
          "Error while reading configuration for class "
              + configurationBeanType.getType().getTypeName()
              + " with base-keys "
              + Arrays.toString(baseKeys),
          e);
    }
  }

  public void readFromYaml(String yamlSource, String... baseKeys) {
    readFromYaml(yamlSource, ConfigurationValuePrecedence.ADDITIONAL_YAML, baseKeys);
  }

  public void readFromYaml(
      String yamlSource, ConfigurationValuePrecedence precedence, String... baseKeys) {
    readConfigurationFile(yamlSource, precedence, ConfigurationFileType.YAML, baseKeys);
  }

  public void readConfigurationFile(
      String yamlSource,
      ConfigurationValuePrecedence precedence,
      ConfigurationFileType configurationFileType,
      String... baseKeys) {
    initialize();

    val values = configurationFileType.loadFromString(yamlSource);
    final HashMap<TigerConfigurationKey, String> valueMap = new HashMap<>();
    addConfigurationFileToMap(values, new TigerConfigurationKey(baseKeys), valueMap);
    DeprecatedKeysUsageChecker.checkForDeprecatedKeys(valueMap);

    sourcesManager.addNewSource(
        BasicTigerConfigurationSource.builder().values(valueMap).precedence(precedence).build());
  }

  public boolean readBoolean(String key) {
    return parseBoolean(readString(key));
  }

  public boolean readBoolean(String key, boolean defValue) {
    return readStringOptional(key).map(TigerConfigurationLoader::parseBoolean).orElse(defValue);
  }

  public Optional<Boolean> readBooleanOptional(String key) {
    return readStringOptional(key).map(TigerConfigurationLoader::parseBoolean);
  }

  public void loadEnvironmentVariables() {
    sourcesManager
        .getSortedStream()
        .filter(source -> source.getPrecedence() == ConfigurationValuePrecedence.ENV)
        .forEach(sourcesManager::removeSource);

    sourcesManager.addNewSource(
        BasicTigerConfigurationSource.builder()
            .values(
                System.getenv().entrySet().stream()
                    .collect(
                        Collectors.toMap(
                            entry -> new TigerConfigurationKey(entry.getKey()),
                            Map.Entry::getValue,
                            (e1, e2) -> mapConflictResolver(e1, e2, "environment variables"))))
            .precedence(ConfigurationValuePrecedence.ENV)
            .build());
  }

  public void loadSystemProperties() {
    sourcesManager
        .getSortedStream()
        .filter(source -> source.getPrecedence() == ConfigurationValuePrecedence.PROPERTIES)
        .forEach(sourcesManager::removeSource);

    sourcesManager.addNewSource(
        BasicTigerConfigurationSource.builder()
            .values(
                System.getProperties().entrySet().stream()
                    .collect(
                        Collectors.toMap(
                            entry -> new TigerConfigurationKey(entry.getKey().toString()),
                            entry -> entry.getValue().toString(),
                            (e1, e2) -> mapConflictResolver(e1, e2, "system properties"))))
            .precedence(ConfigurationValuePrecedence.PROPERTIES)
            .build());
  }

  /**
   * Generates a map containing all key/value pairs. All placeholders below the reference in the
   * values are resolved.
   */
  public Map<TigerConfigurationKey, String> retrieveMap(TigerConfigurationKey reference) {
    final Map<TigerConfigurationKey, String> map = retrieveMapUnresolved();
    replacePlaceholders(map, reference);
    return map;
  }

  /**
   * Generates a map containing all key/value pairs. Placeholders in the values are NOT resolved.
   */
  public Map<TigerConfigurationKey, String> retrieveMapUnresolved() {
    Map<TigerConfigurationKey, String> loadedAndSortedProperties = new HashMap<>();

    for (AbstractTigerConfigurationSource configurationSource :
        sourcesManager.getSortedListReversed()) {
      loadedAndSortedProperties =
          configurationSource.addValuesToMap(loadedAndSortedProperties);
    }

    return loadedAndSortedProperties;
  }

  /**
   * Generates a tree containing all key/value pairs. Placeholders in the values are NOT resolved.
   */
  private JsonNode convertToTreeUnresolved() {
    return convertMapToTree(retrieveMapUnresolved());
  }

  private JsonNode convertMapToTree(Map<TigerConfigurationKey, String> map) {
    final ObjectNode result = new ObjectNode(objectMapper.getNodeFactory());

    for (Entry<TigerConfigurationKey, String> entry : map.entrySet()) {
      createAndReturnDeepPath(entry.getKey(), result)
          .put(entry.getKey().get(entry.getKey().size() - 1).getValue(), entry.getValue());
    }
    return mapObjectsToArrayWhereApplicable(result, objectMapper.getNodeFactory());
  }

  private void replacePlaceholders(
      Map<TigerConfigurationKey, String> loadedAndSortedProperties,
      TigerConfigurationKey reference) {
    final Map<TigerConfigurationKey, String> updatedValues =
        loadedAndSortedProperties.entrySet().stream()
            .filter(entry -> entry.getValue().contains("${") && entry.getValue().contains("}"))
            .filter(entry -> entry.getKey().isBelow(reference))
            .map(
                entry ->
                    Pair.of(
                        entry.getKey(), TokenSubstituteHelper.substitute(entry.getValue(), this)))
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));

    loadedAndSortedProperties.putAll(updatedValues);
  }

  private JsonNode mapObjectsToArrayWhereApplicable(JsonNode value, JsonNodeFactory nodeFactory) {
    if (value instanceof ObjectNode asObjectNode) {
      if (isArray(asObjectNode)) {
        return new ArrayNode(
            nodeFactory,
            StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(value.fields(), Spliterator.ORDERED), false)
                .sorted(Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .map(node -> mapObjectsToArrayWhereApplicable(node, nodeFactory))
                .toList());
      } else {
        return new ObjectNode(
            nodeFactory,
            StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(asObjectNode.fields(), Spliterator.ORDERED),
                    false)
                .map(
                    entry ->
                        Pair.of(
                            entry.getKey(),
                            mapObjectsToArrayWhereApplicable(entry.getValue(), nodeFactory)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
      }
    } else {
      return value;
    }
  }

  private boolean isArray(ObjectNode value) {
    Spliterator<String> stringSpliterator =
        Spliterators.spliteratorUnknownSize(value.fieldNames(), Spliterator.ORDERED);
    if (StreamSupport.stream(stringSpliterator, false).anyMatch(s -> !NumberUtils.isParsable(s))) {
      return false;
    }
    final List<Integer> keys =
        StreamSupport.stream(stringSpliterator, false)
            .mapToInt(Integer::parseInt)
            .sorted()
            .boxed()
            .toList();
    int i = 0;
    for (Integer key : keys) {
      if (key != i++) {
        return false;
      }
    }
    return true;
  }

  private ObjectNode createAndReturnDeepPath(
      final List<TigerConfigurationKeyString> keys, ObjectNode position) {
    if (keys.size() > 1) {
      for (TigerConfigurationKeyString key : keys.subList(0, keys.size() - 1)) {
        String homogenizedKey = homogenizeKeysInMapAndReturnCorrectedKey(position, key);

        if (!position.has(homogenizedKey)) {
          position.putObject(homogenizedKey);
        }
        if (!(position.get(homogenizedKey) instanceof ObjectNode)) {
          continue;
        }
        position = (ObjectNode) position.get(homogenizedKey);
      }
    }
    return position;
  }

  private String homogenizeKeysInMapAndReturnCorrectedKey(
      ObjectNode position, TigerConfigurationKeyString key) {
    for (Iterator<String> it = position.fieldNames(); it.hasNext(); ) {
      String toBeReplacedKey = it.next();
      if (key.getValue().equals(toBeReplacedKey)
          || !key.getValue().equalsIgnoreCase(toBeReplacedKey) /* do we have a clash? */) {
        continue;
      }
      if (!toBeReplacedKey.equals(toBeReplacedKey.toLowerCase())) {
        // only select cases where field is all lower case
        return toBeReplacedKey;
      }
      final JsonNode leaf = position.remove(toBeReplacedKey);
      position.set(key.getValue(), leaf);
      return key.getValue();
    }
    return key.getValue();
  }

  public Map<String, String> readMap(String... baseKeys) {
    var reference = new TigerConfigurationKey(baseKeys);
    return retrieveMap(reference).entrySet().stream()
        .filter(entry -> entry.getKey().isBelow(reference))
        .collect(
            Collectors.toMap(
                entry -> entry.getKey().subtractFromBeginning(reference).downsampleKey(),
                Entry::getValue));
  }

  public List<String> readList(String... baseKeys) {
    var reference = new TigerConfigurationKey(baseKeys);
    return retrieveMap(reference).entrySet().stream()
        .filter(entry -> entry.getKey().isBelow(reference))
        .map(Entry::getValue)
        .toList();
  }

  public Map<String, String> readMapWithCaseSensitiveKeys(String... baseKeys) {
    return readMapWithCaseSensitiveKeys(new TigerConfigurationKey(baseKeys));
  }

  public Map<String, String> readMapWithCaseSensitiveKeys(TigerConfigurationKey reference) {
    return retrieveMap(reference).entrySet().stream()
        .filter(entry -> entry.getKey().isBelow(reference))
        .collect(
            Collectors.toMap(
                entry ->
                    entry.getKey().subtractFromBeginning(reference).downsampleKeyCaseSensitive(),
                Entry::getValue));
  }

  public List<AbstractTigerConfigurationSource> listSources() {
    return sourcesManager.getSortedListReversed();
  }

  public void putValue(String key, Object value) {
    putValue(key, value, ConfigurationValuePrecedence.RUNTIME_EXPORT);
  }

  public void putValue(String key, Object value, ConfigurationValuePrecedence precedence) {
    putValue(new TigerConfigurationKey(key), value, precedence);
  }

  public void putValue(
      TigerConfigurationKey key, Object value, ConfigurationValuePrecedence precedence) {
    if (value == null) {
      throw new TigerConfigurationException(
          "Trying to store null-value. Only non-values are allowed!");
    }

    final AbstractTigerConfigurationSource configurationSource =
        sourcesManager
            .getSortedStream()
            .filter(source -> source.getPrecedence() == precedence)
            .findAny()
            .orElseGet(() -> generateNewConfigurationSource(precedence));

    if (value instanceof String asString) {
      configurationSource.putValue(key, asString);
    } else {
      try {
        Yaml yaml = new Yaml(new DuplicateMapKeysForbiddenConstructor());
        final HashMap<TigerConfigurationKey, String> valueMap = new HashMap<>();
        addConfigurationFileToMap(yaml.load(objectMapper.writeValueAsString(value)), key, valueMap);

        valueMap.forEach(configurationSource::putValue);
      } catch (JsonProcessingException e) {
        throw new TigerConfigurationException("Error during serialization", e);
      }
    }
  }

  private AbstractTigerConfigurationSource generateNewConfigurationSource(
      ConfigurationValuePrecedence precedence) {
    final AbstractTigerConfigurationSource newSource =
        new BasicTigerConfigurationSource(precedence);
    sourcesManager.addNewSource(newSource);
    return newSource;
  }

  public void addConfigurationSource(AbstractTigerConfigurationSource configurationSource) {
    sourcesManager.addNewSource(configurationSource);
  }

  public boolean removeConfigurationSource(AbstractTigerConfigurationSource configurationSource) {
    return sourcesManager.removeSource(configurationSource);
  }
}
