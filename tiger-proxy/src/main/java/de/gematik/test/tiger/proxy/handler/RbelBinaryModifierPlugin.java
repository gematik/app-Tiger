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
package de.gematik.test.tiger.proxy.handler;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyModifierDescription;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyStartupException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

public interface RbelBinaryModifierPlugin {

  static final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  Set<Class<? extends RbelBinaryModifierPlugin>> pluginClassList = new HashSet<>();

  Optional<byte[]> modify(RbelElement target, RbelConverter rbelConverter);

  static RbelBinaryModifierPlugin instantiateModifierPlugin(
      TigerProxyModifierDescription modifierDescription) {
    try {
      Class<? extends RbelBinaryModifierPlugin> clazz = findClass(modifierDescription.getName());
      return buildClassInstance(clazz, modifierDescription);
    } catch (Exception e) {
      throw new TigerProxyStartupException(
          "Error while instantiating modifier plugin class '" + modifierDescription.getName() + "'",
          e);
    }
  }

  static RbelBinaryModifierPlugin buildClassInstance(
      Class<? extends RbelBinaryModifierPlugin> clazz,
      TigerProxyModifierDescription modifierDescription) {
    return objectMapper.convertValue(modifierDescription.getParameters(), clazz);
  }

  static Class<? extends RbelBinaryModifierPlugin> findClass(String className) {
    if (pluginClassList.isEmpty()) {
      Reflections reflections =
          new Reflections(
              new ConfigurationBuilder()
                  .setUrls(ClasspathHelper.forJavaClassPath())
                  .setScanners(new SubTypesScanner(false)));
      pluginClassList.addAll(reflections.getSubTypesOf(RbelBinaryModifierPlugin.class));
    }
    return pluginClassList.stream()
        .filter(
            clazz -> clazz.getName().equals(className) || clazz.getSimpleName().equals(className))
        .findFirst()
        .orElseThrow();
  }
}
