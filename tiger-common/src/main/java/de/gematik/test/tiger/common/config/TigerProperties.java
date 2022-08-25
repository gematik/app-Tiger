/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.Properties;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class TigerProperties {

    private String buildVersion = "";
    private String buildDate = "";

    public TigerProperties(URL url) {
        Properties properties = new Properties();
        if (url == null) {
            log.warn("Unable to find build.properties at {}", url);
            buildVersion = "?.?.?";
            buildDate = ZonedDateTime.now().toLocalDate().toString();
        } else {
            try (InputStream inputStream = url.openStream()) {
                properties.load(inputStream);
            } catch (FileNotFoundException fie) {
                log.warn("File 'build.properties' not found.");
            } catch (IOException e) {
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
    }
    
    public TigerProperties() {
        this(ClassLoader.getSystemResource("build.properties"));
    }

    public String getFullBuildVersion() {
        return "Version: " + getBuildVersion() + " - " + getBuildDate();
    }
}
