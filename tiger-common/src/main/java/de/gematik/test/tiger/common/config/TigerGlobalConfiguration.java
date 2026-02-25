/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.common.config;

import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.*;
import static de.gematik.test.tiger.common.util.FunctionWithCheckedException.unchecked;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.common.data.config.AdditionalConfigurationFileProperty;
import de.gematik.test.tiger.common.data.config.ConfigurationFileType;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
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
import lombok.val;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Central configuration store. All sources (Environment-variables, YAML-files, local exports) end
 * up here, and all configuration is loaded from here (Testenv-mgr, local tiger-proxy, test-lib
 * configuration and also user-defined values).
 */
@Slf4j
public class TigerGlobalConfiguration {

  public static final String TIGER_BASEKEY = "tiger";
  private static final TigerConfigurationLoader globalConfigurationLoader =
      new TigerConfigurationLoader();
  private static final int NUMBER_OF_FREE_PORTS = 256;
  public static final String ADDITIONAL_CONFIGURATION_FILES = "additionalConfigurationFiles";
  @Getter @Setter private static boolean requireTigerYaml = false;
  private static boolean initialized = false;

  private TigerGlobalConfiguration() {}

  public static synchronized void reset() {
    globalConfigurationLoader.reset();
    initialized = false;
    requireTigerYaml = false;
  }

  public static void initialize() {
    TigerJexlExecutor.initialize();
    initializeWithCliProperties(Map.of());
  }

  public static synchronized void initializeWithCliProperties(
      Map<String, String> additionalProperties) {
    if (initialized) {
      return;
    }

    initialized = true;
    globalConfigurationLoader.initialize();

    globalConfigurationLoader.loadEnvironmentVariables();
    globalConfigurationLoader.loadSystemProperties();

    if (additionalProperties != null) {
      additionalProperties.forEach(
          (key, value) ->
              TigerGlobalConfiguration.putValue(key, value, ConfigurationValuePrecedence.CLI));
    }

    var fixedPorts = addFreePortVariables();
    addHostnameVariable();
    readMainYamlFile();
    readHostYamlFile();
    readProfileYamlFile();
    readAdditionalConfigurationFiles();
    addFixedPortVariables(fixedPorts);
  }

  private static void readProfileYamlFile() {
    // environment variables and system properties should have already been loaded in the tiger
    // global configuration

    val profileFile =
        globalConfigurationLoader
            .readStringOptional("profile")
            .map(TigerGlobalConfiguration::profileFileName)
            .map(TigerGlobalConfiguration::resolveRelativePathToTigerYaml)
            .map(Path::toFile);

    if (profileFile.isPresent()) {
      val f = profileFile.get();
      if (!f.exists()) {
        throw new TigerConfigurationException(
            "Could not find profile configuration-file '" + f.getAbsolutePath() + "'.");
      }
      log.info("Reading profile configuration from file '{}'", f.getAbsolutePath());
      readYamlFile(
          profileFile.get(), Optional.of(TIGER_BASEKEY), ConfigurationValuePrecedence.PROFILE_YAML);
    } else {
      TIGER_DEFAULT_PROFILE
          .getValue()
          .map(TigerGlobalConfiguration::profileFileName)
          .map(TigerGlobalConfiguration::resolveRelativePathToTigerYaml)
          .map(Path::toFile)
          .filter(File::exists)
          .ifPresent(
              f -> {
                log.info("Reading profile configuration from file '{}'", f.getAbsolutePath());
                readYamlFile(
                    f, Optional.of(TIGER_BASEKEY), ConfigurationValuePrecedence.PROFILE_YAML);
              });
    }
  }

  private static String profileFileName(String profile) {
    return "tiger-" + profile + ".yaml";
  }

  private static String findBasePathOfMainTigerYaml() {
    return TIGER_TESTENV_CFGFILE_LOCATION
        .getValue()
        .map(File::new)
        .map(File::getParentFile)
        .map(f -> f.getAbsolutePath() + "/")
        .orElse("");
  }

  private static void addFixedPortVariables(List<Integer> fixedPorts) {
    Iterator<Integer> iterator = fixedPorts.iterator();
    for (TigerTypedConfigurationKey<Integer> key :
        List.of(TESTENV_MGR_RESERVED_PORT, LOCALPROXY_ADMIN_RESERVED_PORT)) {
      if (key.getValue().filter(v -> v > 0).isPresent()) {
        continue;
      }
      key.putValue(iterator.next());
    }
  }

  private static void addHostnameVariable() {
    globalConfigurationLoader.putValue(
        "hostname", getComputerName(), ConfigurationValuePrecedence.DEFAULTS);
    globalConfigurationLoader.putValue(
        "canonicalHostname",
        getHostname().getCanonicalHostName(),
        ConfigurationValuePrecedence.DEFAULTS);
    globalConfigurationLoader.putValue(
        "fullHostname", getHostname().getHostName(), ConfigurationValuePrecedence.DEFAULTS);
  }

  private static List<Integer> addFreePortVariables() {
    List<ServerSocket> sockets = new ArrayList<>();
    for (int i = 0; i < NUMBER_OF_FREE_PORTS + 2; i++) {
      try {
        final ServerSocket serverSocket = new ServerSocket(0);
        globalConfigurationLoader.putValue(
            "free.port." + i,
            Integer.toString(serverSocket.getLocalPort()),
            ConfigurationValuePrecedence.RUNTIME_EXPORT);
        sockets.add(serverSocket);
      } catch (IOException e) {
        throw new TigerConfigurationException(
            "Exception while trying to add free port variables", e);
      }
    }
    var result =
        sockets.stream().skip(NUMBER_OF_FREE_PORTS).map(ServerSocket::getLocalPort).toList();
    sockets.forEach(
        serverSocket -> {
          try {
            serverSocket.close();
          } catch (IOException e) {
            throw new TigerConfigurationException(
                "Exception while closing temporary sockets for free port variables", e);
          }
        });
    return result;
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
    return globalConfigurationLoader
        .readStringOptional(key)
        .map(TigerGlobalConfiguration::resolvePlaceholders);
  }

  public static synchronized Optional<String> readStringWithoutResolving(String key) {
    assertGlobalConfigurationIsInitialized();
    return globalConfigurationLoader.readStringOptional(key);
  }

  public static Optional<byte[]> readByteArray(String key) {
    assertGlobalConfigurationIsInitialized();
    return globalConfigurationLoader.readStringOptional(key).map(Base64.getDecoder()::decode);
  }

  @SneakyThrows
  public static synchronized <T> Optional<T> instantiateConfigurationBean(
      Class<T> configurationBeanClass, String... baseKeys) {
    assertGlobalConfigurationIsInitialized();
    return globalConfigurationLoader.instantiateConfigurationBean(configurationBeanClass, baseKeys);
  }

  @SneakyThrows
  public static synchronized <T> T instantiateConfigurationBean(
      TypeReference<T> configurationBeanType, String... baseKeys) {
    assertGlobalConfigurationIsInitialized();
    return globalConfigurationLoader.instantiateConfigurationBean(configurationBeanType, baseKeys);
  }

  /**
   * Retrieve data for a custom type.
   *
   * <p><strong>Example: parse a List of CustomType</strong> <code>
   * <pre>
   * final CollectionType type = TigerGlobalConfiguration.getObjectMapper()
   *                                 .getTypeFactory()
   *                                 .constructCollectionType(List.class, CustomType.class);
   * final List<CustomType> o = TigerGlobalConfiguration.instantiateConfigurationBean(type, "path.to.my.data");
   * </pre>
   * </code>
   */
  @SneakyThrows
  public static synchronized <T> T instantiateConfigurationBean(
      JavaType configurationBeanType, String... baseKeys) {
    assertGlobalConfigurationIsInitialized();
    return globalConfigurationLoader.instantiateConfigurationBean(configurationBeanType, baseKeys);
  }

  public static synchronized void readFromYaml(String yamlSource, String... baseKeys) {
    assertGlobalConfigurationIsInitialized();
    globalConfigurationLoader.readFromYaml(yamlSource, baseKeys);
  }

  public static synchronized void readFromYaml(
      String yamlSource, ConfigurationValuePrecedence precedence, String... baseKeys) {
    assertGlobalConfigurationIsInitialized();
    globalConfigurationLoader.readFromYaml(yamlSource, precedence, baseKeys);
  }

  public static synchronized void readConfigurationFile(
      String yamlSource,
      ConfigurationValuePrecedence precedence,
      ConfigurationFileType fileType,
      String... baseKeys) {
    assertGlobalConfigurationIsInitialized();
    globalConfigurationLoader.readConfigurationFile(yamlSource, precedence, fileType, baseKeys);
  }

  public static synchronized boolean readBoolean(String key) {
    assertGlobalConfigurationIsInitialized();
    return BooleanUtils.toBoolean(readString(key));
  }

  public static synchronized boolean readBoolean(String key, boolean defaultValue) {
    assertGlobalConfigurationIsInitialized();
    return readBooleanOptional(key).orElse(defaultValue);
  }

  public static synchronized Optional<Boolean> readBooleanOptional(String key) {
    assertGlobalConfigurationIsInitialized();
    return readStringOptional(key).map(BooleanUtils::toBoolean);
  }

  private static void assertGlobalConfigurationIsInitialized() {
    if (!initialized) {
      TigerGlobalConfiguration.initialize();
      initialized = true;
    }
  }

  public static List<String> readList(String... baseKeys) {
    assertGlobalConfigurationIsInitialized();
    return globalConfigurationLoader.readList(baseKeys);
  }

  public static Map<String, String> readMap(String... baseKeys) {
    assertGlobalConfigurationIsInitialized();
    return globalConfigurationLoader.readMap(baseKeys);
  }

  public static Map<String, String> readMapWithCaseSensitiveKeys(String... baseKeys) {
    assertGlobalConfigurationIsInitialized();
    return globalConfigurationLoader.readMapWithCaseSensitiveKeys(baseKeys);
  }

  public static List<TigerConfigurationSource> listSources() {
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

  public static void putValue(String key, String value, ConfigurationValuePrecedence precedence) {
    assertGlobalConfigurationIsInitialized();
    globalConfigurationLoader.putValue(key, value, precedence);
  }

  public static void putValue(String key, Object value, ConfigurationValuePrecedence precedence) {
    assertGlobalConfigurationIsInitialized();
    globalConfigurationLoader.putValue(key, value, precedence);
  }

  public static String resolvePlaceholders(String stringToSubstitute) {
    assertGlobalConfigurationIsInitialized();
    return TokenSubstituteHelper.substitute(stringToSubstitute, globalConfigurationLoader);
  }

  public static String resolvePlaceholdersWithContext(
      String stringToSubstitute, TigerJexlContext context) {
    assertGlobalConfigurationIsInitialized();
    return TokenSubstituteHelper.substitute(
        stringToSubstitute, globalConfigurationLoader, Optional.ofNullable(context));
  }

  public static Optional<Integer> readIntegerOptional(String key) {
    assertGlobalConfigurationIsInitialized();
    return readStringOptional(key).map(Integer::parseInt);
  }

  @SneakyThrows
  private static void readMainYamlFile() {
    final Optional<String> tigerYamlValue = TIGER_YAML_VALUE.getValueWithoutResolving();
    TIGER_ROOT_FOLDER.putValue(Path.of(".").toAbsolutePath().toString());
    if (tigerYamlValue.isPresent()) {
      log.info("Reading configuration from tiger.yaml property as string");
      globalConfigurationLoader.readFromYaml(
          tigerYamlValue.get(), ConfigurationValuePrecedence.TEST_YAML, TIGER_BASEKEY);
      return;
    }

    final Optional<File> customCfgFile = TIGER_TESTENV_CFGFILE_LOCATION.getValue().map(File::new);

    if (customCfgFile.isPresent()) {
      if (customCfgFile.get().exists()) {
        readYamlFile(
            customCfgFile.get(),
            Optional.of(TIGER_BASEKEY),
            ConfigurationValuePrecedence.MAIN_YAML);
        setRootFolderNormalized(customCfgFile);
        return;
      } else {
        throw new TigerConfigurationException(
            "Could not find configuration-file '" + customCfgFile.get().getAbsolutePath() + "'.");
      }
    }

    final Optional<File> mainCfgFile =
        Stream.of(TIGER_TESTENV_CFGFILE_LOCATION.getValue().orElse(null), "tiger.yaml", "tiger.yml")
            .filter(Objects::nonNull)
            .map(File::new)
            .filter(File::exists)
            .findFirst();
    if (mainCfgFile.isPresent()) {
      setRootFolderNormalized(mainCfgFile);
      readYamlFile(
          mainCfgFile.get(), Optional.of(TIGER_BASEKEY), ConfigurationValuePrecedence.MAIN_YAML);
      return;
    }

    if (requireTigerYaml) {
      throw new TigerConfigurationException("Could not find configuration-file 'tiger.yaml'.");
    }
  }

  private static void setRootFolderNormalized(Optional<File> customCfgFile) {
    customCfgFile
        .map(unchecked(File::getCanonicalFile))
        .map(File::getParentFile)
        .map(unchecked(File::getCanonicalPath))
        .ifPresent(TIGER_ROOT_FOLDER::putValue);
  }

  private static void readHostYamlFile() {
    String computerName = getComputerName();

    Stream.of("tiger-" + computerName + ".yaml", "tiger-" + computerName + ".yml")
        .map(File::new)
        .filter(File::exists)
        .findFirst()
        .ifPresent(
            hostCfgFile ->
                readYamlFile(
                    hostCfgFile,
                    Optional.of(TIGER_BASEKEY),
                    ConfigurationValuePrecedence.HOST_YAML));
  }

  private static void readAdditionalConfigurationFiles() {
    final List<AdditionalConfigurationFileProperty> additionalConfigurationFiles =
        globalConfigurationLoader.instantiateConfigurationBean(
            new TypeReference<>() {}, TIGER_BASEKEY, ADDITIONAL_CONFIGURATION_FILES);

    for (AdditionalConfigurationFileProperty additionalConfigurationFile :
        additionalConfigurationFiles) {
      val file =
          findAdditionalConfigurationFile(
              TigerGlobalConfiguration.resolvePlaceholders(
                  additionalConfigurationFile.getFilename()));
      readConfigurationFile(
          file.toFile(),
          Optional.ofNullable(additionalConfigurationFile.getBaseKey()),
          ConfigurationValuePrecedence.ADDITIONAL_YAML,
          additionalConfigurationFile.getType());
    }
  }

  private static Path findAdditionalConfigurationFile(String filename) {
    Optional<Path> configFileLocation = TIGER_TESTENV_CFGFILE_LOCATION.getValue().map(Path::of);

    if (configFileLocation.isPresent()) {
      val yamlRelativeToTigerYaml = configFileLocation.get().resolveSibling(filename);
      if (yamlRelativeToTigerYaml.toFile().exists()) {
        return yamlRelativeToTigerYaml;
      }
    }

    Path currentDirectory = Path.of(".");
    val yamlRelativeToWorkingDirectory = currentDirectory.resolveSibling(filename);
    if (yamlRelativeToWorkingDirectory.toFile().exists()) {
      return yamlRelativeToWorkingDirectory;
    }

    throw new TigerConfigurationException(
        "The file "
            + filename
            + " relative to parent folder of tiger.yaml "
            + configFileLocation
            + " or current working directory "
            + currentDirectory
            + " is not found.");
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

  private static void readYamlFile(
      File file, Optional<String> baseKey, ConfigurationValuePrecedence precedence) {
    readConfigurationFile(file, baseKey, precedence, ConfigurationFileType.YAML);
  }

  private static void readConfigurationFile(
      File file,
      Optional<String> baseKey,
      ConfigurationValuePrecedence precedence,
      ConfigurationFileType fileType) {
    try {
      log.info("Reading configuration from file '{}'", file.getAbsolutePath());
      if (baseKey.isPresent()) {
        readConfigurationFile(
            FileUtils.readFileToString(file, StandardCharsets.UTF_8),
            precedence,
            fileType,
            baseKey.get());
      } else {
        readConfigurationFile(
            FileUtils.readFileToString(file, StandardCharsets.UTF_8), precedence, fileType);
      }
    } catch (TigerConfigurationException tcex) {
      throw tcex;
    } catch (IOException | RuntimeException e) {
      throw new TigerConfigurationException(
          "Error while reading configuration from file '"
              + file.getAbsolutePath()
              + "' with cause '"
              + e
              + "'",
          e);
    }
  }

  public static ObjectMapper getObjectMapper() {
    return globalConfigurationLoader.getObjectMapper();
  }

  public static void deleteFromAllSources(TigerConfigurationKey configurationKey) {
    globalConfigurationLoader.listSources().forEach(source -> source.removeValue(configurationKey));
  }

  public static void deleteFromSourceAndLowerPrecedence(
      TigerConfigurationKey configurationKey, ConfigurationValuePrecedence precedence) {
    globalConfigurationLoader.listSources().stream()
        .filter(source -> source.getPrecedence().getValue() <= precedence.getValue())
        .forEach(source -> source.removeValue(configurationKey));
  }

  public static void dangerouslyDeleteAllProperties() {
    globalConfigurationLoader.reset();
  }

  public static Map<String, Pair<ConfigurationValuePrecedence, String>> exportConfiguration() {
    var sources =
        globalConfigurationLoader.listSources().stream()
            .sorted(
                Comparator.comparing(
                        TigerConfigurationSource::getPrecedence,
                        Comparator.comparing(ConfigurationValuePrecedence::getValue))
                    .reversed());

    Map<String, Pair<ConfigurationValuePrecedence, String>> exportedConfiguration = new HashMap<>();

    sources
        .sequential()
        .forEach(
            s ->
                s.getValues()
                    .forEach(
                        (k, v) ->
                            exportedConfiguration.put(
                                k.downsampleKeyCaseSensitive(), Pair.of(s.getPrecedence(), v))));

    return Collections.unmodifiableMap(exportedConfiguration);
  }

  /**
   * Clears local test variables. These are variables from the source
   * ConfigurationValuePrecedence.LOCAL_TEST_CASE_CONTEXT, which should only be active during a
   * single test case.
   */
  public static void clearLocalTestVariables() {
    globalConfigurationLoader.listSources().stream()
        .filter(s -> s.getPrecedence() == ConfigurationValuePrecedence.LOCAL_TEST_CASE_CONTEXT)
        .findAny()
        .ifPresent(globalConfigurationLoader::removeConfigurationSource);
  }

  /**
   * Clears test variables. These are variables from the source
   * ConfigurationValuePrecedence.TEST_CONTEXT, which should only be active during the execution of
   * a feature file.
   */
  public static void clearTestVariables() {
    globalConfigurationLoader.listSources().stream()
        .filter(s -> s.getPrecedence() == ConfigurationValuePrecedence.TEST_CONTEXT)
        .findAny()
        .ifPresent(globalConfigurationLoader::removeConfigurationSource);
  }

  public static Path resolveRelativePathToTigerYaml(String relativePath) {
    return Path.of(TigerGlobalConfiguration.findBasePathOfMainTigerYaml())
        .resolve(relativePath)
        .normalize()
        .toAbsolutePath();
  }
}
