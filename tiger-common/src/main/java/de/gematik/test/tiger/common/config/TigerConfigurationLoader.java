/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import static de.gematik.test.tiger.common.config.TigerConfigurationKeyString.wrapAsKey;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.yaml.snakeyaml.Yaml;

@Slf4j
public class TigerConfigurationLoader {

    private ObjectMapper objectMapper;
    private List<AbstractTigerConfigurationSource> loadedSources;
    private List<TigerTemplateSource> loadedTemplates;

    public TigerConfigurationLoader() {
        initialize();
    }

    public void reset() {
        loadedSources.clear();
        loadedTemplates.clear();
    }

    public void initialize() {
        if (objectMapper == null) {
            objectMapper = JsonMapper.builder()
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CASE)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .build();
        }

        if (loadedSources == null) {
            loadedSources = new ArrayList<>();
        }
        if (loadedTemplates == null) {
            loadedTemplates = new ArrayList<>();
        }
    }

    public String readString(String key) {
        return readStringOptional(key)
            .orElseThrow(() -> new TigerConfigurationException("Could not find value for '" + key + "'"));
    }

    public String readString(String key, String defaultValue) {
        return readStringOptional(key)
            .orElse(defaultValue);
    }

    public Optional<String> readStringOptional(String key) {
        TigerConfigurationKey splittedKey = new TigerConfigurationKey(
            TokenSubstituteHelper.substitute(key, this));
        return loadedSources.stream()
            .sorted(Comparator.comparing(source -> source.getSourceType().getPrecedence()))
            .filter(source -> source.getValues().containsKey(splittedKey))
            .map(source -> source.getValues().get(splittedKey))
            .findFirst();
    }

    @SneakyThrows
    public <T> Optional<T> instantiateConfigurationBean(Class<T> configurationBeanClass, String... baseKeys) {
        initialize();

        TreeNode targetTree = convertToTree();
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
            log.debug("Error while converting the following tree: {}", objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(targetTree));
            throw new TigerConfigurationException(
                "Error while reading configuration for class " + configurationBeanClass.getName() + " with base-keys "
                    + baseKeys, e);
        }
    }

    @SneakyThrows
    public <T> T instantiateConfigurationBean(TypeReference<T> configurationBeanType, String... baseKeys) {
        initialize();

        TreeNode targetTree = convertToTree();
        final TigerConfigurationKey configurationKey = new TigerConfigurationKey(baseKeys);
        for (TigerConfigurationKeyString key : configurationKey) {
            if (targetTree.get(key.getValue()) == null) {
                return objectMapper.readValue("[]", configurationBeanType);
            }
            targetTree = targetTree.get(key.getValue());
        }
        try {
            return objectMapper.treeAsTokens(targetTree).readValueAs(configurationBeanType);
        } catch (JacksonException e) {
            log.debug("Error while converting the following tree: {}", objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(targetTree));
            throw new TigerConfigurationException(
                "Error while reading configuration for class " + configurationBeanType.getType().getTypeName()
                    + " with base-keys " + baseKeys, e);
        }
    }

    public void readFromYaml(String yamlSource, String... baseKeys) {
        readFromYaml(yamlSource, SourceType.YAML, baseKeys);
    }

    public void readFromYaml(String yamlSource, SourceType sourceType, String... baseKeys) {
        initialize();

        Yaml yaml = new Yaml(new DuplicateMapKeysForbiddenConstructor());
        final HashMap<TigerConfigurationKey, String> valueMap = new HashMap<>();
        addYamlToMap(yaml.load(yamlSource), new TigerConfigurationKey(baseKeys), valueMap);
        DeprecatedKeysForbiddenUsageChecker.checkForDeprecatedKeys(valueMap);

        loadedSources.add(BasicTigerConfigurationSource.builder()
            .values(valueMap)
            .sourceType(sourceType)
            .basePath(new TigerConfigurationKey(baseKeys))
            .build());
    }

    public boolean readBoolean(String key) {
        final String rawValue = readString(key);
        if (rawValue.equals("1")) {
            return true;
        }
        return Boolean.parseBoolean(rawValue);
    }

    public boolean readBoolean(String key, boolean defValue) {
        final String rawValue = readString(key, defValue ? "1" : "0");
        if (rawValue.equals("1")) {
            return true;
        }
        return Boolean.parseBoolean(rawValue);
    }

    public void readTemplates(String templatesYaml, String... baseKeys) {
        Yaml yaml = new Yaml(new DuplicateMapKeysForbiddenConstructor());
        final Object loadedYaml = yaml.load(templatesYaml);

        if (!(loadedYaml instanceof Map)
            || (!((Map) loadedYaml).containsKey("templates"))
            || (!(((Map) loadedYaml).get("templates") instanceof List))) {
            throw new TigerConfigurationException(
                "Error while loading templates: Expected templates-nodes with list of templates");
        }

        ((List) ((Map) loadedYaml).get("templates")).stream()
            .filter(o -> o instanceof Map)
            .map(Map.class::cast)
            .filter(m -> ((Map) m).containsKey("templateName"))
            .forEach(m -> loadedTemplates.add(TigerTemplateSource.builder()
                .templateName(((Map) m).get("templateName").toString())
                .targetPath(new TigerConfigurationKey(baseKeys))
                .values(addYamlToMap(m, new TigerConfigurationKey(), new HashMap<>()))
                .build()));
    }

    public void loadEnvironmentVariables() {
        loadedSources.stream()
            .filter(source -> source.getSourceType() == SourceType.ENV)
            .findAny().ifPresent(loadedSources::remove);

        loadedSources.add(BasicTigerConfigurationSource.builder()
            .basePath(new TigerConfigurationKey())
            .values(System.getenv().entrySet().stream()
                .collect(Collectors.toMap(
                    entry -> new TigerConfigurationKey(entry.getKey()),
                    Map.Entry::getValue)))
            .sourceType(SourceType.ENV)
            .build());
    }

    public void loadSystemProperties() {
        loadedSources.stream()
            .filter(source -> source.getSourceType() == SourceType.PROPERTIES)
            .findAny().ifPresent(loadedSources::remove);

        loadedSources.add(BasicTigerConfigurationSource.builder()
            .basePath(new TigerConfigurationKey())
            .values(System.getProperties().entrySet().stream()
                .collect(Collectors.toMap(
                    entry -> new TigerConfigurationKey(entry.getKey().toString()),
                    entry -> entry.getValue().toString())))
            .sourceType(SourceType.PROPERTIES)
            .build());
    }

    public Map<TigerConfigurationKey, String> retrieveMap() {
        Map<TigerConfigurationKey, String> loadedAndSortedProperties = new HashMap<>();

        for (AbstractTigerConfigurationSource configurationSource : loadedSources.stream()
            .sorted(Comparator.comparing(AbstractTigerConfigurationSource::getSourceType))
            .collect(Collectors.toList())) {
            loadedAndSortedProperties = configurationSource.applyTemplatesAndAddValuesToMap(
                loadedTemplates,
                loadedAndSortedProperties
            );
        }

        replacePlaceholders(loadedAndSortedProperties);

        return loadedAndSortedProperties;
    }

    private JsonNode convertToTree() {
        final ObjectNode result = new ObjectNode(objectMapper.getNodeFactory());

        for (var entry : retrieveMap().entrySet()) {
            createAndReturnDeepPath(entry.getKey(), result)
                .put(entry.getKey().get(entry.getKey().size() - 1).getValue(), entry.getValue());
        }
        return mapObjectsToArrayWhereApplicable(result, objectMapper.getNodeFactory());
    }

    private void replacePlaceholders(Map<TigerConfigurationKey, String> loadedAndSortedProperties) {
        final Map<TigerConfigurationKey, String> updatedValues = loadedAndSortedProperties.entrySet().stream()
            .filter(entry -> entry.getValue().contains("${")
                && entry.getValue().contains("}"))
            .map(entry -> Pair.of(entry.getKey(),
                TokenSubstituteHelper.substitute(entry.getValue(), this)))
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));

        loadedAndSortedProperties.putAll(updatedValues);
    }

    private JsonNode mapObjectsToArrayWhereApplicable(JsonNode value, JsonNodeFactory nodeFactory) {
        if (value instanceof ObjectNode) {
            if (isArray((ObjectNode) value)) {
                return new ArrayNode(nodeFactory,
                    StreamSupport.stream(Spliterators.spliteratorUnknownSize(value.fields(), Spliterator.ORDERED),
                            false)
                        .sorted(Comparator.comparing(Map.Entry::getKey))
                        .map(Map.Entry::getValue)
                        .map(node -> mapObjectsToArrayWhereApplicable(node, nodeFactory))
                        .collect(Collectors.toList()));
            } else {
                return new ObjectNode(nodeFactory,
                    StreamSupport.stream(Spliterators.spliteratorUnknownSize(value.fields(), Spliterator.ORDERED),
                            false)
                        .map(entry -> Pair.of(entry.getKey(),
                            mapObjectsToArrayWhereApplicable(entry.getValue(), nodeFactory)))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            }
        } else {
            return value;
        }
    }

    private boolean isArray(ObjectNode value) {
        if (IteratorUtils.toList(value.fieldNames()).stream()
            .anyMatch(s -> !NumberUtils.isParsable(s.toString()))) {
            return false;
        }
        final List<Integer> keys = IteratorUtils.toList(value.fieldNames()).stream()
            .mapToInt(s -> Integer.parseInt(s.toString()))
            .sorted()
            .boxed()
            .collect(Collectors.toList());
        int i = 0;
        for (Integer key : keys) {
            if (key != i++) {
                return false;
            }
            continue;
        }
        return true;
    }

    private ObjectNode createAndReturnDeepPath(final List<TigerConfigurationKeyString> keys, ObjectNode position) {
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

    private String homogenizeKeysInMapAndReturnCorrectedKey(ObjectNode position, TigerConfigurationKeyString key) {
        for (Iterator<String> it = position.fieldNames(); it.hasNext(); ) {
            String toBeReplacedKey = it.next();
            if (key.getValue().equals(toBeReplacedKey)) {
                continue;
            }
            if (!key.getValue().equalsIgnoreCase(toBeReplacedKey)) { // do we have a clash?
                continue;
            }
            if (!toBeReplacedKey.equals(
                toBeReplacedKey.toLowerCase())) { // only select cases where field is all lower case
                return toBeReplacedKey;
            }
            final JsonNode leaf = position.remove(toBeReplacedKey);
            position.set(key.getValue(), leaf);
            return key.getValue();
        }
        return key.getValue();
    }

    private Map<TigerConfigurationKey, String> addYamlToMap(
        final Object value,
        final TigerConfigurationKey baseKeys,
        final Map<TigerConfigurationKey, String> valueMap) {
        if (value instanceof Map) {
            ((Map<String, ?>) value).entrySet()
                .forEach(entry -> {
                    var newList = new TigerConfigurationKey(baseKeys);
                    newList.add(entry.getKey());
                    addYamlToMap(entry.getValue(), newList, valueMap);
                });
        } else if (value instanceof List) {
            int counter = 0;
            for (Object entry : (List) value) {
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

    public Map<String, String> readMap(String... baseKeys) {
        var reference = new TigerConfigurationKey(baseKeys);
        return retrieveMap().entrySet().stream()
            .filter(entry -> entry.getKey().isBelow(reference))
            .collect(Collectors.toMap(
                entry -> entry.getKey().subtractFromBeginning(reference).downsampleKey(),
                e -> e.getValue()));
    }

    public List<AbstractTigerConfigurationSource> listSources() {
        return Collections.unmodifiableList(loadedSources);
    }

    public void putValue(String key, String value) {
        putValue(key, value, SourceType.RUNTIME_EXPORT);
    }

    public void putValue(String key, Object value) {
        try {
            Yaml yaml = new Yaml(new DuplicateMapKeysForbiddenConstructor());
            final HashMap<TigerConfigurationKey, String> valueMap = new HashMap<>();
            addYamlToMap(yaml.load(objectMapper.writeValueAsString(value)), new TigerConfigurationKey(key), valueMap);
            loadedSources.add(BasicTigerConfigurationSource.builder()
                .values(valueMap)
                .sourceType(SourceType.RUNTIME_EXPORT)
                .basePath(new TigerConfigurationKey(key))
                .build());
        } catch (JsonProcessingException e) {
            throw new TigerConfigurationException("Error during serialization", e);
        }
    }

    public void putValue(String key, String value, SourceType sourceType) {
        final Optional<AbstractTigerConfigurationSource> configurationSource = loadedSources.stream()
            .filter(source -> source.getSourceType() == sourceType)
            .findAny();
        if (configurationSource.isEmpty()) {
            final AbstractTigerConfigurationSource newSource;
            if (sourceType == SourceType.THREAD_CONTEXT) {
                newSource = new TigerThreadScopedConfigurationSource();
            } else {
                newSource = new BasicTigerConfigurationSource(sourceType);
            }
            loadedSources.add(newSource);
            newSource.putValue(new TigerConfigurationKey(key), value);
        } else {
            configurationSource.get().getValues().put(new TigerConfigurationKey(key), value);
        }
    }

    public void addConfigurationSource(AbstractTigerConfigurationSource configurationSource) {
        loadedSources.add(configurationSource);
    }

    public boolean removeConfigurationSource(AbstractTigerConfigurationSource configurationSource) {
        return loadedSources.remove(configurationSource);
    }
}
