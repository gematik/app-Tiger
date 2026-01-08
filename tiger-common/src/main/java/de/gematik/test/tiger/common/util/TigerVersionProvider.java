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
package de.gematik.test.tiger.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TigerVersionProvider {

  private static final String BUILD_PROPERTIES_RESOURCE = "/build.properties";
  private static final String TIGER_VERSION = "tiger.version";
  private static final String TIGER_BUILD_DATE = "tiger.buildDate";
  private static final String UNKNOWN = "<unknown>";

  private final Properties BUILD_PROPERTIES = loadBuildProperties();

  public String getTigerVersionString() {
    final String version = BUILD_PROPERTIES.getProperty(TIGER_VERSION, UNKNOWN);
    final String buildDate = BUILD_PROPERTIES.getProperty(TIGER_BUILD_DATE, UNKNOWN);
    if (UNKNOWN.equals(buildDate)) {
      return version;
    }
    return version + "-" + buildDate;
  }

  private static Properties loadBuildProperties() {
    Properties properties = new Properties();
    try (InputStream resource =
        TigerVersionProvider.class.getResourceAsStream(BUILD_PROPERTIES_RESOURCE)) {
      if (resource != null) {
        properties.load(resource);
      }
    } catch (IOException ignored) {
      // fall back to defaults
    }
    return properties;
  }
}
