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
