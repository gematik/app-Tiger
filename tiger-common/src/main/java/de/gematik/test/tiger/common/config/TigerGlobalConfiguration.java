/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.common.config;

import com.fasterxml.jackson.core.type.TypeReference;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.common.data.config.AdditionalYamlProperty;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

/**
 * Central configuration store. All sources (Environment-variables, YAML-files, local exports) end up here and all configuration is loaded
 * from here (Testenv-mgr, local tiger-proxy, test-lib configuration and also user-defined values).
 */
@Slf4j
public class TigerGlobalConfiguration {

    private static TigerConfigurationLoader globalConfigurationLoader = new TigerConfigurationLoader();
    @Getter
    @Setter
    private static boolean requireTigerYaml = false;
    private static boolean initialized = false;

    public synchronized static void reset() {
        globalConfigurationLoader.reset();
        initialized = false;
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

        globalConfigurationLoader.loadSystemProperties();
        globalConfigurationLoader.loadEnvironmentVariables();

        if (additionalProperties != null) {
            additionalProperties
                .forEach((key, value) -> TigerGlobalConfiguration.putValue(key, value, SourceType.CLI));
        }

        addFreePortVariables();
        readYamlFiles();

        readAdditionalYamlFiles();
    }

    private static void addFreePortVariables() {
        List<ServerSocket> sockets = new ArrayList<>();
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
                throw new TigerConfigurationException("Exception while closing temporary sockets for free port variables", e);
            }
        });
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
    public synchronized static <T> Optional<T> instantiateConfigurationBean(Class<T> configurationBeanClass,
        String... baseKeys) {
        assertGlobalConfigurationIsInitialized();
        return globalConfigurationLoader.instantiateConfigurationBean(configurationBeanClass, baseKeys);
    }

    @SneakyThrows
    public synchronized static <T> T instantiateConfigurationBean(TypeReference<T> configurationBeanType, String... baseKeys) {
        assertGlobalConfigurationIsInitialized();
        return globalConfigurationLoader.instantiateConfigurationBean(configurationBeanType, baseKeys);
    }

    public synchronized static void readFromYaml(String yamlSource, String... baseKeys) {
        assertGlobalConfigurationIsInitialized();
        globalConfigurationLoader.readFromYaml(yamlSource, baseKeys);
    }

    public synchronized static void readFromYaml(String yamlSource, SourceType sourceType, String... baseKeys) {
        assertGlobalConfigurationIsInitialized();
        globalConfigurationLoader.readFromYaml(yamlSource, sourceType, baseKeys);
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

    public static Optional<Integer> readIntegerOptional(String key) {
        assertGlobalConfigurationIsInitialized();
        return readStringOptional(key)
            .map(Integer::parseInt);
    }

    private static void readYamlFiles() {
        TigerGlobalConfiguration.readStringOptional("TIGER_YAML")
            .ifPresent(s -> globalConfigurationLoader.readFromYaml(s, SourceType.TEST_YAML, "tiger"));

        final Optional<File> customCfgFile = TigerGlobalConfiguration.readStringOptional(
                "TIGER_TESTENV_CFGFILE")
            .map(File::new);
        if (customCfgFile.isPresent()) {
            if (customCfgFile.get().exists()) {
                readYamlFile(customCfgFile.get(), Optional.of("tiger"));
                return;
            } else {
                throw new TigerConfigurationException("Could not find configuration-file '"
                    + customCfgFile.get().getAbsolutePath() + "'.");
            }
        }

        String computerName = getComputerName();

        final Optional<File> cfgFile = Stream.of(
                TigerGlobalConfiguration.readStringOptional("TIGER_TESTENV_CFGFILE").orElse(null),
                "tiger-" + computerName + ".yaml", "tiger-" + computerName + ".yml",
                "tiger.yaml", "tiger.yml")
            .filter(Objects::nonNull)
            .map(File::new)
            .filter(File::exists)
            .findFirst();
        if (cfgFile.isPresent()) {
            readYamlFile(cfgFile.get(), Optional.of("tiger"));
            return;
        }

        final Optional<File> oldCfgFile = Stream.of(
                TigerGlobalConfiguration.readStringOptional("TIGER_TESTENV_CFGFILE").orElse(null),
                "tiger-testenv-" + computerName + ".yaml", "tiger-testenv-" + computerName + ".yml",
                "tiger-testenv.yaml", "tiger-testenv.yml")
            .filter(Objects::nonNull)
            .map(File::new)
            .filter(File::exists)
            .findFirst();
        if (oldCfgFile.isPresent()) {
            log.warn("Older file format detected! Will be deprecated in upcoming versions. Please use tiger.yaml!");
            readYamlFile(oldCfgFile.get(), Optional.of("tiger"));
            return;
        }

        if (requireTigerYaml) {
            throw new TigerConfigurationException("Could not find configuration-file 'tiger.yaml'.");
        }
    }

    private static void readAdditionalYamlFiles() {
        final List<AdditionalYamlProperty> additionalYamls = globalConfigurationLoader.instantiateConfigurationBean(
            new TypeReference<>() {
            }, "tiger", "additionalYamls");

        for (AdditionalYamlProperty additionalYaml : additionalYamls) {
            readYamlFile(Optional.ofNullable(additionalYaml.getFilename())
                    .filter(Objects::nonNull)
                    .map(TigerGlobalConfiguration::resolvePlaceholders)
                    .map(File::new)
                    .filter(File::exists)
                    .orElseThrow(() -> new TigerConfigurationException(
                        "Unable to locate file from configuration " + additionalYaml)),
                Optional.ofNullable(additionalYaml.getBaseKey()));
        }
    }

    private static String getComputerName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return InetAddress.getLoopbackAddress().getHostName();
        }
    }

    private static void readYamlFile(File file, Optional<String> baseKey) {
        try {
            log.info("Reading configuration from file '{}'", file.getAbsolutePath());
            if (baseKey.isPresent()) {
                readFromYaml(FileUtils.readFileToString(file, StandardCharsets.UTF_8), baseKey.get());
            } else {
                readFromYaml(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException e) {
            throw new TigerConfigurationException(
                "Error while reading configuration from file '" + file.getAbsolutePath() + "'", e);
        }
    }

    /**
     * Returns a local scope in which values can be added and code executed. This enables the use of very local values that can not (or
     * should not) creep over into other parts of your testsuite.
     *
     * @return
     */
    public static TigerScopedExecutor localScope() {
        return new TigerScopedExecutor();
    }

    static void addConfigurationSource(AbstractTigerConfigurationSource configurationSource) {
        globalConfigurationLoader.addConfigurationSource(configurationSource);
    }

    static boolean removeConfigurationSource(AbstractTigerConfigurationSource configurationSource) {
        return globalConfigurationLoader.removeConfigurationSource(configurationSource);
    }
}
