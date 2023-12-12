/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import static de.gematik.test.tiger.common.config.TigerConfigurationKeyString.wrapAsKey;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.cfg.ContextAttributes;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.zion.config.TigerSkipEvaluation;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
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
  private ObjectMapper strictObjectMapper;
  private List<TigerTemplateSource> loadedTemplates;

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

  public static Map<TigerConfigurationKey, String> addYamlToMap(
      final Object value,
      final TigerConfigurationKey baseKeys,
      final Map<TigerConfigurationKey, String> valueMap) {
    if (value instanceof Map<?, ?> asMap) {
      asMap.forEach(
          (key, value1) -> {
            var newList = new TigerConfigurationKey(baseKeys);
            newList.add((String) key);
            addYamlToMap(value1, newList, valueMap);
          });
    } else if (value instanceof List<?> asList) {
      int counter = 0;
      for (Object entry : asList) {
        TigerConfigurationKey newList = new TigerConfigurationKey(baseKeys);
        newList.add(wrapAsKey(Integer.toString(counter++)));
        addYamlToMap(entry, newList, valueMap);
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
    loadedTemplates.clear();
    initializeObjectMapper();
  }

  public void initialize() {
    if (objectMapper == null) {
      initializeObjectMapper();
    }
    if (loadedTemplates == null) {
      loadedTemplates = new ArrayList<>();
    }
  }

  private void initializeObjectMapper() {
    SimpleModule skipEvaluationModule = new SimpleModule();
    skipEvaluationModule.addDeserializer(String.class, new SkipEvaluationDeserializer(this));
    objectMapper =
        JsonMapper.builder()
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CASE)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .addModule(new JavaTimeModule())
            .addModule(new AllowDelayedPrimitiveResolvementModule(this))
            .addModule(skipEvaluationModule)
            .defaultAttributes(
                ContextAttributes.getEmpty()
                    .withSharedAttributes(Map.of(TIGER_CONFIGURATION_ATTRIBUTE_KEY, this)))
            .build();
    strictObjectMapper =
        JsonMapper.builder()
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CASE)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .addModule(new JavaTimeModule())
            .addModule(new AllowDelayedPrimitiveResolvementModule(this))
            .addModule(skipEvaluationModule)
            .defaultAttributes(
                ContextAttributes.getEmpty()
                    .withSharedAttributes(Map.of(TIGER_CONFIGURATION_ATTRIBUTE_KEY, this)))
            .build();
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

  /**
   * Instantiates a bean of the given class. The base-keys denote the point from which the keys are
   * taken. If values can not be substituted (e.g. ${key.that.does.not.exist}) an empty value is
   * returned. This behaviour follows the "fail fast, fail early" approach.
   *
   * <p>If the base-keys lead to a non-defined (i.e. empty) node in the tree (no values have been
   * read) an empty optional is returned.
   *
   * @param configurationBeanClass The class of the configuration bean
   * @param baseKeys Where in the configuration tree should the values be taken from?
   * @return An instance of configurationBeanClass filled with values taken from the configuration
   *     tree
   */
  public <T> Optional<T> instantiateConfigurationBeanStrict(
      Class<T> configurationBeanClass, String... baseKeys) {
    return instantiateConfigurationBean(configurationBeanClass, strictObjectMapper, baseKeys);
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
    readFromYaml(yamlSource, SourceType.ADDITIONAL_YAML, baseKeys);
  }

  public void readFromYaml(String yamlSource, SourceType sourceType, String... baseKeys) {
    initialize();

    Yaml yaml = new Yaml(new DuplicateMapKeysForbiddenConstructor());
    final HashMap<TigerConfigurationKey, String> valueMap = new HashMap<>();
    addYamlToMap(yaml.load(yamlSource), new TigerConfigurationKey(baseKeys), valueMap);
    DeprecatedKeysForbiddenUsageChecker.checkForDeprecatedKeys(valueMap);

    sourcesManager.addNewSource(
        BasicTigerConfigurationSource.builder()
            .values(valueMap)
            .sourceType(sourceType)
            .basePath(new TigerConfigurationKey(baseKeys))
            .build());
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

  public void readTemplates(String templatesYaml, String... baseKeys) {
    Yaml yaml = new Yaml(new DuplicateMapKeysForbiddenConstructor());
    final Object loadedYaml = yaml.load(templatesYaml);

    if (loadedYaml instanceof Map<?, ?> asMap
        && asMap.containsKey("templates")
        && asMap.get("templates") instanceof List<?> aslist) {
      aslist.stream()
          .filter(Map.class::isInstance)
          .map(Map.class::cast)
          .filter(m -> m.containsKey("templateName"))
          .forEach(
              m ->
                  loadedTemplates.add(
                      TigerTemplateSource.builder()
                          .templateName(m.get("templateName").toString())
                          .targetPath(new TigerConfigurationKey(baseKeys))
                          .values(addYamlToMap(m, new TigerConfigurationKey(), new HashMap<>()))
                          .build()));
    } else {
      throw new TigerConfigurationException(
          "Error while loading templates: Expected templates-nodes with list of templates");
    }
  }

  public void loadEnvironmentVariables() {
    sourcesManager
        .getSortedStream()
        .filter(source -> source.getSourceType() == SourceType.ENV)
        .forEach(sourcesManager::removeSource);

    sourcesManager.addNewSource(
        BasicTigerConfigurationSource.builder()
            .basePath(new TigerConfigurationKey())
            .values(
                System.getenv().entrySet().stream()
                    .collect(
                        Collectors.toMap(
                            entry -> new TigerConfigurationKey(entry.getKey()),
                            Map.Entry::getValue,
                            (e1, e2) -> mapConflictResolver(e1, e2, "environment variables"))))
            .sourceType(SourceType.ENV)
            .build());
  }

  public void loadSystemProperties() {
    sourcesManager
        .getSortedStream()
        .filter(source -> source.getSourceType() == SourceType.PROPERTIES)
        .forEach(sourcesManager::removeSource);

    sourcesManager.addNewSource(
        BasicTigerConfigurationSource.builder()
            .basePath(new TigerConfigurationKey())
            .values(
                System.getProperties().entrySet().stream()
                    .collect(
                        Collectors.toMap(
                            entry -> new TigerConfigurationKey(entry.getKey().toString()),
                            entry -> entry.getValue().toString(),
                            (e1, e2) -> mapConflictResolver(e1, e2, "system properties"))))
            .sourceType(SourceType.PROPERTIES)
            .build());
  }

  /** Generates a map containing all key/value pairs. Placeholders in the values ARE resolved. */
  public Map<TigerConfigurationKey, String> retrieveMap() {
    final Map<TigerConfigurationKey, String> map = retrieveMapUnresolved();
    replacePlaceholders(map);
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
          configurationSource.applyTemplatesAndAddValuesToMap(
              loadedTemplates, loadedAndSortedProperties);
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

    for (var entry : map.entrySet()) {
      createAndReturnDeepPath(entry.getKey(), result)
          .put(entry.getKey().get(entry.getKey().size() - 1).getValue(), entry.getValue());
    }
    return mapObjectsToArrayWhereApplicable(result, objectMapper.getNodeFactory());
  }

  private void replacePlaceholders(Map<TigerConfigurationKey, String> loadedAndSortedProperties) {
    final Map<TigerConfigurationKey, String> updatedValues =
        loadedAndSortedProperties.entrySet().stream()
            .filter(entry -> entry.getValue().contains("${") && entry.getValue().contains("}"))
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
    return retrieveMap().entrySet().stream()
        .filter(entry -> entry.getKey().isBelow(reference))
        .collect(
            Collectors.toMap(
                entry -> entry.getKey().subtractFromBeginning(reference).downsampleKey(),
                Entry::getValue));
  }

  public List<String> readList(String... baseKeys) {
    var reference = new TigerConfigurationKey(baseKeys);
    return retrieveMap().entrySet().stream()
        .filter(entry -> entry.getKey().isBelow(reference))
        .map(Entry::getValue)
        .toList();
  }

  public Map<String, String> readMapWithCaseSensitiveKeys(String... baseKeys) {
    return readMapWithCaseSensitiveKeys(new TigerConfigurationKey(baseKeys));
  }

  public Map<String, String> readMapWithCaseSensitiveKeys(TigerConfigurationKey reference) {
    return retrieveMap().entrySet().stream()
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

  public void putValue(String key, String value) {
    if (value == null) {
      throw new TigerConfigurationException(
          "Trying to store null-value. Only non-values are allowed!");
    }
    putValue(key, value, SourceType.RUNTIME_EXPORT);
  }

  public void putValue(String key, Object value) {
    if (value == null) {
      throw new TigerConfigurationException(
          "Trying to store null-value. Only non-values are allowed!");
    }
    if (value instanceof String asString) {
      putValue(key, asString);
    } else {
      try {
        Yaml yaml = new Yaml(new DuplicateMapKeysForbiddenConstructor());
        final HashMap<TigerConfigurationKey, String> valueMap = new HashMap<>();
        addYamlToMap(
            yaml.load(objectMapper.writeValueAsString(value)),
            new TigerConfigurationKey(key),
            valueMap);
        sourcesManager.addNewSource(
            BasicTigerConfigurationSource.builder()
                .values(valueMap)
                .sourceType(SourceType.RUNTIME_EXPORT)
                .basePath(new TigerConfigurationKey(key))
                .build());
      } catch (JsonProcessingException e) {
        throw new TigerConfigurationException("Error during serialization", e);
      }
    }
  }

  public void putValue(String key, String value, SourceType sourceType) {
    final Optional<AbstractTigerConfigurationSource> configurationSource =
        sourcesManager
            .getSortedStream()
            .filter(source -> source.getSourceType() == sourceType)
            .findAny();
    if (configurationSource.isEmpty()) {
      final AbstractTigerConfigurationSource newSource;
      if (sourceType == SourceType.THREAD_CONTEXT) {
        newSource = new TigerThreadScopedConfigurationSource();
      } else {
        newSource = new BasicTigerConfigurationSource(sourceType);
      }
      sourcesManager.addNewSource(newSource);
      newSource.putValue(new TigerConfigurationKey(key), value);
    } else {
      configurationSource.get().putValue(new TigerConfigurationKey(key), value);
    }
  }

  public void addConfigurationSource(AbstractTigerConfigurationSource configurationSource) {
    sourcesManager.addNewSource(configurationSource);
  }

  public boolean removeConfigurationSource(AbstractTigerConfigurationSource configurationSource) {
    return sourcesManager.removeSource(configurationSource);
  }

  @AllArgsConstructor
  private static class AllowDelayedPrimitiveResolvementModule extends Module {

    private TigerConfigurationLoader tigerConfigurationLoader;

    @Override
    public String getModuleName() {
      return "fallback provider";
    }

    @Override
    public Version version() {
      return Version.unknownVersion();
    }

    @Override
    public void setupModule(SetupContext setupContext) {
      setupContext.addDeserializationProblemHandler(
          new ClazzFallbackConverter(tigerConfigurationLoader));
    }
  }

  @RequiredArgsConstructor
  @AllArgsConstructor
  @Slf4j
  public static class SkipEvaluationDeserializer extends JsonDeserializer<String>
      implements ContextualDeserializer {

    private final TigerConfigurationLoader configurationLoader;
    private boolean skipEvaluation;

    @Override
    public JsonDeserializer<?> createContextual(
        DeserializationContext ctxt, BeanProperty property) {
      this.skipEvaluation =
          property != null && property.getAnnotation(TigerSkipEvaluation.class) != null;
      return new SkipEvaluationDeserializer(configurationLoader, skipEvaluation);
    }

    @Override
    public String deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
        throws IOException {
      final String valueAsString = jsonParser.getValueAsString();
      if (skipEvaluation) {
        return valueAsString;
      } else {
        return TokenSubstituteHelper.substitute(valueAsString, configurationLoader);
      }
    }
  }

  @AllArgsConstructor
  private static class ClazzFallbackConverter extends DeserializationProblemHandler {

    TigerConfigurationLoader tigerConfigurationLoader;

    @Override
    public Object handleWeirdStringValue(
        DeserializationContext ctxt, Class<?> targetType, String valueToConvert, String failureMsg)
        throws IOException {
      if (valueToConvert.contains("!{") || valueToConvert.contains("${")) {
        final String substitute =
            TokenSubstituteHelper.substitute(valueToConvert, tigerConfigurationLoader);
        if (!substitute.equals(valueToConvert)) {
          final TextNode replacedTextNode = ctxt.getNodeFactory().textNode(substitute);
          return ctxt.readTreeAsValue(replacedTextNode, targetType);
        }
        return returnTigerSpecificFallbackValue(ctxt, targetType, valueToConvert, failureMsg);
      }
      return super.handleWeirdStringValue(ctxt, targetType, valueToConvert, failureMsg);
    }

    Object returnTigerSpecificFallbackValue(
        DeserializationContext ctxt, Class<?> targetType, String valueToConvert, String failureMsg)
        throws IOException {
      if (targetType.equals(Boolean.class)
          || targetType.equals(Integer.class)
          || targetType.equals(Long.class)
          || targetType.equals(Character.class)
          || targetType.equals(Double.class)
          || targetType.equals(Float.class)
          || targetType.equals(Byte.class)
          || targetType.equals(Short.class)) {
        return null;
      } else if (targetType.equals(boolean.class)) {
        return false;
      } else if (targetType.equals(int.class)) {
        return -1;
      } else if (targetType.equals(long.class)) {
        return (long) -1;
      } else if (targetType.equals(double.class)) {
        return -1.;
      } else if (targetType.equals(float.class)) {
        return -1f;
      } else if (targetType.equals(short.class)) {
        return (short) -1;
      } else if (targetType.equals(char.class)) {
        return ' ';
      } else if (targetType.equals(byte.class)) {
        return (byte) -1;
      } else {
        return super.handleWeirdStringValue(ctxt, targetType, valueToConvert, failureMsg);
      }
    }
  }
}
