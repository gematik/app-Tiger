package de.gematik.test.tiger.common.config;

import java.util.Optional;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TigerGlobalConfiguration {

    private static TigerConfigurationLoader globalConfigurationLoader = new TigerConfigurationLoader();
    private static boolean initialized = false;

    public synchronized static void reset() {
        globalConfigurationLoader.reset();
        initialized = false;
    }

    public synchronized static void initialize() {
        if (initialized) {
            return;
        }

        initialized = true;
        globalConfigurationLoader.initialize();
    }

    public synchronized static String readString(String key) {
        assertGlobalConfigurationIsInitialized();
        return globalConfigurationLoader.readString(key);
    }

    public synchronized static String readString(String key, String defaultValue) {
        assertGlobalConfigurationIsInitialized();
        return globalConfigurationLoader.readString(key, defaultValue);
    }

    public synchronized static Optional<String> readStringOptional(String key) {
        assertGlobalConfigurationIsInitialized();
        return globalConfigurationLoader.readStringOptional(key);
    }

    @SneakyThrows
    public synchronized static <T extends Object> T instantiateConfigurationBean(Class<T> configurationBeanClass, String... baseKeys) {
        assertGlobalConfigurationIsInitialized();
        return globalConfigurationLoader.instantiateConfigurationBean(configurationBeanClass, baseKeys);
    }

    public synchronized static void readFromYaml(String yamlSource, String... baseKeys) {
        assertGlobalConfigurationIsInitialized();
        globalConfigurationLoader.readFromYaml(yamlSource, baseKeys);
    }

    public synchronized static boolean readBoolean(String key) {
        assertGlobalConfigurationIsInitialized();
        return globalConfigurationLoader.readBoolean(key);
    }

    public synchronized static boolean readBoolean(String key, boolean defaultValue) {
        assertGlobalConfigurationIsInitialized();
        return globalConfigurationLoader.readBoolean(key, defaultValue);
    }

    public synchronized static void readTemplates(String templatesYaml, String... baseKeys) {
        assertGlobalConfigurationIsInitialized();
        globalConfigurationLoader.readTemplates(templatesYaml, baseKeys);
    }

    private static void assertGlobalConfigurationIsInitialized() {
        if (!initialized) {
            TigerGlobalConfiguration.initialize();
            initialized = true;
        }
    }
}
