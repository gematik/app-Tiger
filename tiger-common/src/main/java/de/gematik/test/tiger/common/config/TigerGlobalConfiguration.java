package de.gematik.test.tiger.common.config;

import de.gematik.test.tiger.common.TokenSubstituteHelper;
import io.netty.util.internal.SocketUtils;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
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

        globalConfigurationLoader.loadSystemProperties();
        globalConfigurationLoader.loadEnvironmentVariables();

        addFreePortVariables();
    }

    private static void addFreePortVariables() {
        for (int i = 0; i < 256; i++) {
            try {
                final ServerSocket serverSocket = new ServerSocket(0);
                globalConfigurationLoader.putValue("free.port." + i,
                    Integer.toString(serverSocket.getLocalPort()),
                    SourceType.RUNTIME_EXPORT);
                serverSocket.close();
            } catch (IOException e) {
                throw new TigerConfigurationException("Exception while trying to add free port variables", e);
            }
        }
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

    public static Map<String, String> readMap(String... baseKeys) {
        assertGlobalConfigurationIsInitialized();
        return globalConfigurationLoader.readMap(baseKeys);
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
        globalConfigurationLoader.putValue(key, value.toString());
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

    public static Optional<Integer> readIntegerOptional(String key) {
        assertGlobalConfigurationIsInitialized();
        return readStringOptional(key)
            .map(Integer::parseInt);
    }
}
