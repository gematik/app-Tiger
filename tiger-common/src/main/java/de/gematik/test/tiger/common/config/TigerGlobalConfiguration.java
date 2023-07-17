/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.LOCALPROXY_ADMIN_RESERVED_PORT;
import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.TESTENV_MGR_RESERVED_PORT;
import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.TIGER_TESTENV_CFGFILE_LOCATION;
import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.TIGER_YAML_VALUE;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.common.data.config.AdditionalYamlProperty;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

/**
 * Central configuration store. All sources (Environment-variables, YAML-files, local exports) end up here and all
 * configuration is loaded from here (Testenv-mgr, local tiger-proxy, test-lib configuration and also user-defined
 * values).
 */
@Slf4j
public class TigerGlobalConfiguration {

    private static final TigerConfigurationLoader globalConfigurationLoader = new TigerConfigurationLoader();
    public static final String TIGER_BASEKEY = "tiger";
    @Getter
    @Setter
    private static boolean requireTigerYaml = false;
    private static boolean initialized = false;

    public static synchronized void reset() {
        globalConfigurationLoader.reset();
        initialized = false;
        requireTigerYaml = false;
    }

    public static void initialize() {
        initializeWithCliProperties(Map.of());
    }

    public static void initializeWithCliProperties(Map<String, String> additionalProperties) {
        if (initialized) {
            return;
        }

        initialized = true;
        globalConfigurationLoader.initialize();

        globalConfigurationLoader.loadEnvironmentVariables();
        globalConfigurationLoader.loadSystemProperties();

        if (additionalProperties != null) {
            additionalProperties
                .forEach((key, value) -> TigerGlobalConfiguration.putValue(key, value, SourceType.CLI));
        }

        addFreePortVariables();
        addHostnameVariable();
        readMainYamlFile();
        readHostYamlFile();

        readAdditionalYamlFiles();
    }

    private static void addHostnameVariable() {
        globalConfigurationLoader.putValue("hostname", getComputerName(), SourceType.DEFAULTS);
        globalConfigurationLoader.putValue("canonicalHostname", getHostname().getCanonicalHostName(), SourceType.DEFAULTS);
        globalConfigurationLoader.putValue("fullHostname", getHostname().getHostName(), SourceType.DEFAULTS);
    }

    private static void addFreePortVariables() {
        List<ServerSocket> sockets = new ArrayList<>();
        for (TigerTypedConfigurationKey<Integer> key : List.of(TESTENV_MGR_RESERVED_PORT,
            LOCALPROXY_ADMIN_RESERVED_PORT)) {
            try {
                final ServerSocket serverSocket = new ServerSocket(0);
                key.putValue(serverSocket.getLocalPort());
                sockets.add(serverSocket);
            } catch (IOException e) {
                throw new TigerConfigurationException("Exception while trying to add tiger internal port variables", e);
            }
        }
        for (int i = 0; i < 256; i++) {
            try {
                final ServerSocket serverSocket = new ServerSocket(0);
                globalConfigurationLoader.putValue("free.port." + i,
                    Integer.toString(serverSocket.getLocalPort()),
                    SourceType.RUNTIME_EXPORT);
                sockets.add(serverSocket);
            } catch (IOException e) {
                throw new TigerConfigurationException("Exception while trying to add free port variables", e);
            }
        }
        sockets.forEach(serverSocket -> {
            try {
                serverSocket.close();
            } catch (IOException e) {
                throw new TigerConfigurationException(
                    "Exception while closing temporary sockets for free port variables", e);
            }
        });
    }

    public static synchronized String readString(String key) {
        assertGlobalConfigurationIsInitialized();
        return resolvePlaceholders(globalConfigurationLoader.readString(key));
    }

    public static synchronized String readString(String key, String defaultValue) {
        assertGlobalConfigurationIsInitialized();
        return resolvePlaceholders(globalConfigurationLoader.readString(key, defaultValue));
    }

    public static synchronized Optional<String> readStringOptional(String key) {
        assertGlobalConfigurationIsInitialized();
        return globalConfigurationLoader.readStringOptional(key)
            .map(TigerGlobalConfiguration::resolvePlaceholders);
    }

    @SneakyThrows
    public static synchronized <T> Optional<T> instantiateConfigurationBean(Class<T> configurationBeanClass,
        String... baseKeys) {
        assertGlobalConfigurationIsInitialized();
        return globalConfigurationLoader.instantiateConfigurationBean(configurationBeanClass, baseKeys);
    }

    @SneakyThrows
    public static synchronized <T> Optional<T> instantiateConfigurationBeanStrict(Class<T> configurationBeanClass,
        String... baseKeys) {
        assertGlobalConfigurationIsInitialized();
        return globalConfigurationLoader.instantiateConfigurationBeanStrict(configurationBeanClass, baseKeys);
    }

    @SneakyThrows
    public static synchronized <T> T instantiateConfigurationBean(TypeReference<T> configurationBeanType,
        String... baseKeys) {
        assertGlobalConfigurationIsInitialized();
        return globalConfigurationLoader.instantiateConfigurationBean(configurationBeanType, baseKeys);
    }

    public static synchronized void readFromYaml(String yamlSource, String... baseKeys) {
        assertGlobalConfigurationIsInitialized();
        globalConfigurationLoader.readFromYaml(yamlSource, baseKeys);
    }

    public static synchronized void readFromYaml(String yamlSource, SourceType sourceType, String... baseKeys) {
        assertGlobalConfigurationIsInitialized();
        globalConfigurationLoader.readFromYaml(yamlSource, sourceType, baseKeys);
    }

    public static synchronized boolean readBoolean(String key) {
        assertGlobalConfigurationIsInitialized();
        return globalConfigurationLoader.readBoolean(key);
    }

    public static synchronized boolean readBoolean(String key, boolean defaultValue) {
        assertGlobalConfigurationIsInitialized();
        return globalConfigurationLoader.readBoolean(key, defaultValue);
    }

    public static synchronized Optional<Boolean> readBooleanOptional(String key) {
        assertGlobalConfigurationIsInitialized();
        return globalConfigurationLoader.readBooleanOptional(key);
    }

    public static synchronized void readTemplates(String templatesYaml, String... baseKeys) {
        assertGlobalConfigurationIsInitialized();
        globalConfigurationLoader.readTemplates(templatesYaml, baseKeys);
    }

    private static void assertGlobalConfigurationIsInitialized() {
        if (!initialized) {
            TigerGlobalConfiguration.initialize();
            initialized = true;
        }
    }

    public static Map<String, String> readMap(String... baseKeys) {
        assertGlobalConfigurationIsInitialized();
        return globalConfigurationLoader.readMap(baseKeys);
    }

    public static Map<String, String> readMapWithCaseSensitiveKeys(String... baseKeys) {
        assertGlobalConfigurationIsInitialized();
        return globalConfigurationLoader.readMapWithCaseSensitiveKeys(baseKeys);
    }

    public static List<AbstractTigerConfigurationSource> listSources() {
        assertGlobalConfigurationIsInitialized();
        return globalConfigurationLoader.listSources();
    }

    public static void putValue(String key, String value) {
        assertGlobalConfigurationIsInitialized();
        globalConfigurationLoader.putValue(key, value);
    }

    public static void putValue(String key, Object value) {
        assertGlobalConfigurationIsInitialized();
        globalConfigurationLoader.putValue(key, value);
    }

    public static void putValue(String key, long value) {
        assertGlobalConfigurationIsInitialized();
        globalConfigurationLoader.putValue(key, Long.toString(value));
    }

    public static void putValue(String key, boolean value) {
        assertGlobalConfigurationIsInitialized();
        globalConfigurationLoader.putValue(key, Boolean.toString(value));
    }

    public static void putValue(String key, double value) {
        assertGlobalConfigurationIsInitialized();
        globalConfigurationLoader.putValue(key, Double.toString(value));
    }

    public static void putValue(String key, int value) {
        assertGlobalConfigurationIsInitialized();
        globalConfigurationLoader.putValue(key, Integer.toString(value));
    }

    public static void putValue(String key, String value, SourceType sourceType) {
        assertGlobalConfigurationIsInitialized();
        globalConfigurationLoader.putValue(key, value, sourceType);
    }

    public static void putValue(String key, Object value, SourceType sourceType) {
        assertGlobalConfigurationIsInitialized();
        globalConfigurationLoader.putValue(key, value.toString(), sourceType);
    }

    public static String resolvePlaceholders(String stringToSubstitute) {
        assertGlobalConfigurationIsInitialized();
        return TokenSubstituteHelper.substitute(stringToSubstitute, globalConfigurationLoader);
    }

    public static String resolvePlaceholdersWithContext(String stringToSubstitute, TigerJexlContext context) {
        assertGlobalConfigurationIsInitialized();
        return TokenSubstituteHelper.substitute(stringToSubstitute, globalConfigurationLoader, Optional.ofNullable(context));
    }

    public static Optional<Integer> readIntegerOptional(String key) {
        assertGlobalConfigurationIsInitialized();
        return readStringOptional(key)
            .map(Integer::parseInt);
    }

    private static void readMainYamlFile() {
        final Optional<String> tigerYamlValue = TIGER_YAML_VALUE.getValue();
        if (tigerYamlValue.isPresent()) {
            log.info("Reading configuration from tiger.yaml property as string");
            globalConfigurationLoader.readFromYaml(tigerYamlValue.get(), SourceType.TEST_YAML, TIGER_BASEKEY);
            return;
        }

        final Optional<File> customCfgFile = TIGER_TESTENV_CFGFILE_LOCATION.getValue()
            .map(File::new);

        if (customCfgFile.isPresent()) {
            if (customCfgFile.get().exists()) {
                readYamlFile(customCfgFile.get(), Optional.of(TIGER_BASEKEY), SourceType.MAIN_YAML);
                return;
            } else {
                throw new TigerConfigurationException("Could not find configuration-file '"
                    + customCfgFile.get().getAbsolutePath() + "'.");
            }
        }

        final Optional<File> mainCfgFile = Stream.of(
                TIGER_TESTENV_CFGFILE_LOCATION.getValue().orElse(null),
                "tiger.yaml", "tiger.yml")
            .filter(Objects::nonNull)
            .map(File::new)
            .filter(File::exists)
            .findFirst();
        if (mainCfgFile.isPresent()) {
            readYamlFile(mainCfgFile.get(), Optional.of(TIGER_BASEKEY), SourceType.MAIN_YAML);
            return;
        }

        if (requireTigerYaml) {
            throw new TigerConfigurationException("Could not find configuration-file 'tiger.yaml'.");
        }
    }

    private static void readHostYamlFile() {
        String computerName = getComputerName();

        Stream.of(
                "tiger-" + computerName + ".yaml", "tiger-" + computerName + ".yml")
            .map(File::new)
            .filter(File::exists)
            .findFirst()
            .ifPresent(hostCfgFile -> readYamlFile(hostCfgFile, Optional.of(TIGER_BASEKEY), SourceType.HOST_YAML));
    }

    private static void readAdditionalYamlFiles() {
        final List<AdditionalYamlProperty> additionalYamls = globalConfigurationLoader.instantiateConfigurationBean(
            new TypeReference<>() {
            }, TIGER_BASEKEY, "additionalYamls");

        for (AdditionalYamlProperty additionalYaml : additionalYamls) {
            File additionalYamlFile = findAdditionalYamlFile(
                TigerGlobalConfiguration.resolvePlaceholders(additionalYaml.getFilename()));
            readYamlFile(additionalYamlFile, Optional.ofNullable(additionalYaml.getBaseKey()), SourceType.ADDITIONAL_YAML);
        }
    }

    private static File findAdditionalYamlFile(String additionalYaml) {
        Optional<Path> configFileLocation = TIGER_TESTENV_CFGFILE_LOCATION.getValue()
            .map(Path::of);

        if (configFileLocation.isPresent()) {
            final File yamlRelativeToTigerYaml
                = configFileLocation.get().resolveSibling(additionalYaml).toFile();
            if (yamlRelativeToTigerYaml.exists()) {
                return yamlRelativeToTigerYaml;
            }
        }

        Path currentDirectory = Path.of(".");
        final File yamlRelativeToWorkingDirectory
            = currentDirectory.resolveSibling(additionalYaml).toFile();
        if (yamlRelativeToWorkingDirectory.exists()) {
            return yamlRelativeToWorkingDirectory;
        }

        throw new TigerConfigurationException(
            "The file " + additionalYaml + " relative to parent folder of tiger.yaml " +
                configFileLocation + " or current working directory " + currentDirectory + " not found.");
    }

    static String getComputerName() {
        return getHostname().getHostName().split("\\.")[0];
    }

    private static InetAddress getHostname() {
        try {
            return InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            return InetAddress.getLoopbackAddress();
        }
    }

    private static void readYamlFile(File file, Optional<String> baseKey, SourceType sourceType) {
        try {
            log.info("Reading configuration from file '{}'", file.getAbsolutePath());
            if (baseKey.isPresent()) {
                readFromYaml(FileUtils.readFileToString(file, StandardCharsets.UTF_8), sourceType, baseKey.get());
            } else {
                readFromYaml(FileUtils.readFileToString(file, StandardCharsets.UTF_8), sourceType);
            }
        } catch (IOException | RuntimeException e) {
            throw new TigerConfigurationException(
                "Error while reading configuration from file '" + file.getAbsolutePath() + "'", e);
        }
    }

    static void addConfigurationSource(AbstractTigerConfigurationSource configurationSource) {
        globalConfigurationLoader.addConfigurationSource(configurationSource);
    }

    static boolean removeConfigurationSource(AbstractTigerConfigurationSource configurationSource) {
        return globalConfigurationLoader.removeConfigurationSource(configurationSource);
    }

    public static ObjectMapper getObjectMapper() {
        return globalConfigurationLoader.getObjectMapper();
    }
}
