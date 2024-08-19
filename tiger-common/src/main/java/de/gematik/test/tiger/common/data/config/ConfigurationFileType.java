package de.gematik.test.tiger.common.data.config;

import de.gematik.test.tiger.common.config.DuplicateMapKeysForbiddenConstructor;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.yaml.snakeyaml.Yaml;

@RequiredArgsConstructor
public enum ConfigurationFileType {
  YAML(ConfigurationFileType::loadYamlFile),
  ENV(ConfigurationFileType::loadPropertiesFile),
  PROPERTY(ConfigurationFileType::loadPropertiesFile);

  private static Object loadYamlFile(String str) {
    return new Yaml(new DuplicateMapKeysForbiddenConstructor()).load(str);
  }

  private static Map<String, String> loadPropertiesFile(String str) {
    try {
      final Properties properties = new Properties();
      properties.load(new StringReader(str));
      return properties.entrySet().stream()
        .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString()));
    } catch (IOException e) {
      throw new TigerConfigurationException("Error while loading properties file", e);
    }
  }

  private final Function<String, Object> loaderFunction;

  public Object loadFromString(String source) {
    return loaderFunction.apply(source);
  }
}
