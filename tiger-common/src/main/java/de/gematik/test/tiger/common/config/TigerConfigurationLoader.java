package de.gematik.test.tiger.common.config;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.parser.ParserException;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static de.gematik.test.tiger.common.config.TigerConfigurationKeyString.wrapAsKey;

@Slf4j
public class TigerConfigurationLoader {

    private ObjectMapper objectMapper;
    private List<TigerConfigurationSource> loadedSources;
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
            objectMapper = new ObjectMapper();
        }

        if (loadedSources == null) {
            loadedSources = new ArrayList<>();
        }
        if (loadedTemplates == null) {
            loadedTemplates = new ArrayList<>();
        }

        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CASE);
        objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
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
        TigerConfigurationKey splittedKey = new TigerConfigurationKey(key);
        return loadedSources.stream()
            .sorted(Comparator.comparing(TigerConfigurationSource::getOrder))
            .filter(source -> source.getValues().containsKey(splittedKey))
            .map(source -> source.getValues().get(splittedKey))
            .findFirst();
    }
    @SneakyThrows
    public <T extends Object> T instantiateConfigurationBean(Class<T> configurationBeanClass, String... baseKeys) {
        initialize();

        TreeNode targetTree = convertToTree();
        for (TigerConfigurationKeyString key : new TigerConfigurationKey(baseKeys)) {
            targetTree = targetTree.get(key.getValue());
        }
        try {
            T resultObject = objectMapper.treeToValue(targetTree, configurationBeanClass);
            return resultObject;
        } catch (JacksonException e) {
            log.debug("Error while converting the following tree: {}", objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(targetTree));
            throw new TigerConfigurationException("Error while reading configuration for class " + configurationBeanClass.getName() + " with base-keys " + baseKeys, e);
        }
    }

    public void readFromYaml(String yamlSource, String... baseKeys) {
        initialize();

        Yaml yaml = new Yaml(new DuplicateMapKeysForbiddenConstructor());
        final HashMap<TigerConfigurationKey, String> valueMap = new HashMap<>();
        addYamlToMap(yaml.load(yamlSource), new TigerConfigurationKey(baseKeys), valueMap);
        loadedSources.add(TigerConfigurationSource.builder()
            .values(valueMap)
            .order(TigerConfigurationSource.SYSTEM_YAML_ORDER)
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
            throw new TigerConfigurationException("Error while loading templates: Expected templates-nodes with list of templates");
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
            .filter(source -> source.getOrder() == TigerConfigurationSource.SYSTEM_ENV_ORDER)
            .findAny().ifPresent(loadedSources::remove);

        loadedSources.add(TigerConfigurationSource.builder()
            .basePath(List.of())
            .values(System.getenv().entrySet().stream()
                .collect(Collectors.toMap(
                    entry -> new TigerConfigurationKey(entry.getKey()),
                    Map.Entry::getValue)))
            .order(TigerConfigurationSource.SYSTEM_ENV_ORDER)
            .build());
    }

    public void loadSystemProperties() {
        loadedSources.stream()
            .filter(source -> source.getOrder() == TigerConfigurationSource.SYSTEM_PROPERTIES_ORDER)
            .findAny().ifPresent(loadedSources::remove);

        loadedSources.add(TigerConfigurationSource.builder()
            .basePath(List.of())
            .values(System.getProperties().entrySet().stream()
                .collect(Collectors.toMap(
                    entry -> new TigerConfigurationKey(entry.getKey().toString()),
                    entry -> entry.getValue().toString())))
            .order(TigerConfigurationSource.SYSTEM_PROPERTIES_ORDER)
            .build());
    }

    public Map<TigerConfigurationKey, String> retrieveMap() {
        Map<TigerConfigurationKey, String> loadedAndSortedProperties = new HashMap<>();

        for (TigerConfigurationSource configurationSource : loadedSources.stream()
            .sorted(Comparator.comparing(TigerConfigurationSource::getOrder).reversed())
            .collect(Collectors.toList())) {
            loadedAndSortedProperties = configurationSource.applyTemplatesAndAddValuesToMap(
                loadedTemplates,
                loadedAndSortedProperties
            );
        }

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

    private JsonNode mapObjectsToArrayWhereApplicable(JsonNode value, JsonNodeFactory nodeFactory) {
        if (value instanceof ObjectNode) {
            if (isArray((ObjectNode) value)) {
                return new ArrayNode(nodeFactory,
                    StreamSupport.stream(Spliterators.spliteratorUnknownSize(value.fields(), Spliterator.ORDERED), false)
                        .sorted(Comparator.comparing(Map.Entry::getKey))
                        .map(Map.Entry::getValue)
                        .map(node -> mapObjectsToArrayWhereApplicable(node, nodeFactory))
                        .collect(Collectors.toList()));
            } else {
                return new ObjectNode(nodeFactory,
                    StreamSupport.stream(Spliterators.spliteratorUnknownSize(value.fields(), Spliterator.ORDERED), false)
                        .map(entry -> Pair.of(entry.getKey(), mapObjectsToArrayWhereApplicable(entry.getValue(), nodeFactory)))
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
            if (!toBeReplacedKey.equals(toBeReplacedKey.toLowerCase())) { // only select cases where field is all lower case
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
                    newList.add(wrapAsKey(entry.getKey()));
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

    public List<TigerConfigurationSource> listSources() {
        return Collections.unmodifiableList(loadedSources);
    }

    /**
     * A specialized {@link Constructor} that checks for duplicate keys.
     */
    public static class DuplicateMapKeysForbiddenConstructor extends SafeConstructor {

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
}
