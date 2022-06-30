/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class TigerProperties {
    private String buildVersion = "";
    private String buildDate = "";

    public TigerProperties() {
      Properties properties = new Properties();
      java.net.URL url = ClassLoader.getSystemResource("build.properties");

      try (InputStream inputStream = url.openStream()){
          properties.load(inputStream);
      } catch (FileNotFoundException fie) {
          log.warn("File 'build.properties' not found.");
      }
      catch (IOException e) {
          log.warn("Problems while reading 'build.properties'.");
      }

      Set<String> keys = properties.stringPropertyNames();
      for (String key : keys) {
          if (key.equalsIgnoreCase("tiger.version")) {
              buildVersion = properties.getProperty(key);
          } else if (key.equalsIgnoreCase("tiger.buildDate")) {
              buildDate = properties.getProperty(key);
          }
      }
    }

    public String getFullBuildVersion() {
        return "Version: " + getBuildVersion() + " - " + getBuildDate();
    }
}
