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

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.apache.commons.lang3.StringUtils;

/** A specialized class that checks for old/deprecated keys. */
public final class DeprecatedKeysUsageChecker {

  private static List<DeprecatedKeyDescriptor> deprecatedKeys =
      List.of(
          DeprecatedKeyDescriptor.builder()
              .compareKey("tiger.servers.*.tigerproxyconfiguration.serverport")
              .deprecatedKey("serverPort")
              .newKey("adminPort")
              .build(),
          DeprecatedKeyDescriptor.builder()
              .compareKey("tiger.tigerproxy.port")
              .deprecatedKey("port")
              .newKey("proxyPort")
              .build(),
          DeprecatedKeyDescriptor.builder()
              .compareKey("tiger.servers.*.tigerproxyconfiguration.proxycfg.port")
              .deprecatedKey("port")
              .newKey("proxyPort")
              .build(),
          DeprecatedKeyDescriptor.builder()
              .compareKey("tiger.servers.*.tigerproxyconfiguration.proxycfg.*")
              .deprecatedKey("proxyCfg")
              .build(),
          DeprecatedKeyDescriptor.builder()
              .compareKey("tiger.servers.*.externaljaroptions.healthcheck")
              .deprecatedKey("tiger.servers.*.externalJarOptions.healthcheck")
              .newKey("tiger.servers.*.healthcheckUrl")
              .build(),
          DeprecatedKeyDescriptor.builder()
              .compareKey("tiger.servers.*.externaljaroptions.healthcheckurl")
              .deprecatedKey("tiger.servers.*.externalJarOptions.healthcheckurl")
              .newKey("tiger.servers.*.healthcheckUrl")
              .build(),
          DeprecatedKeyDescriptor.builder()
              .compareKey("tiger.tigerproxyconfiguration.activateVauAnalysis")
              .deprecatedKey("activateVauAnalysis")
              .newKey("activateRbelParsingFor")
              .build(),
          DeprecatedKeyDescriptor.builder()
              .compareKey("tiger.servers.*.tigerproxyconfiguration.proxycfg.activateVauAnalysis")
              .deprecatedKey("activateVauAnalysis")
              .newKey("activateRbelParsingFor")
              .build(),
          DeprecatedKeyDescriptor.builder()
              .compareKey("tiger.servers.*.tigerproxyconfiguration")
              .deprecatedKey("tigerProxyCfg")
              .newKey("tigerProxyConfiguration")
              .build(),
          DeprecatedKeyDescriptor.builder()
              .compareKey("tiger.tigerproxyconfiguration.activateEpaVauAnalysis")
              .deprecatedKey("activateEpaVauAnalysis")
              .newKey("activateRbelParsingFor")
              .build(),
          DeprecatedKeyDescriptor.builder()
              .compareKey("tiger.tigerproxyconfiguration.activateEpaVauAnalysis")
              .deprecatedKey("activateAsn1Parsing")
              .newKey("activateRbelParsingFor")
              .build(),
          DeprecatedKeyDescriptor.builder()
              .compareKey("tiger.tigerproxyconfiguration.activateEpaVauAnalysis")
              .deprecatedKey("activateAsn1Parsing")
              .newKey("activateRbelParsingFor")
              .build(),
          DeprecatedKeyDescriptor.builder()
              .compareKey("tiger.tigerproxyconfiguration.activateEpaVauAnalysis")
              .deprecatedKey("activateErpVauAnalysis")
              .newKey("activateRbelParsingFor")
              .build(),
          DeprecatedKeyDescriptor.builder()
              .compareKey("tiger.additionalYamls")
              .deprecatedKey("additionalYamls")
              .newKey(TigerGlobalConfiguration.ADDITIONAL_CONFIGURATION_FILES)
              .build(),
          DeprecatedKeyDescriptor.builder()
              .compareKey("tiger.servers.*.tigerproxyconfiguration.proxyRoutes.*.basicAuth")
              .deprecatedKey("basicAuth")
              .newKey("authentication")
              .build(),
          DeprecatedKeyDescriptor.builder()
              .compareKey("tiger.tigerproxy.proxyRoutes.*.basicAuth")
              .deprecatedKey("basicAuth")
              .newKey("authentication")
              .build(),
          DeprecatedKeyDescriptor.builder()
              .compareKey("tiger.servers.*.template")
              .deprecatedKey("template")
              .build(),
          DeprecatedKeyDescriptor.builder()
              .compareKey("tiger.servers.*.pkiKeys")
              .deprecatedKey("pkiKeys")
              .build(),
          DeprecatedKeyDescriptor.builder()
              .compareKey("templates")
              .deprecatedKey("templates")
              .build());

  private DeprecatedKeysUsageChecker() {}

  public static void checkForDeprecatedKeys(TigerConfigurationSource configurationSource) {
    checkForDeprecatedKeys(configurationSource.getValues());
  }

  public static void checkForDeprecatedKeys(Map<TigerConfigurationKey, String> valueMap)
      throws TigerConfigurationException {
    if (valueMap == null) {
      throw new TigerConfigurationException("Tiger configuration map is null!");
    }
    StringJoiner joiner = new StringJoiner("\n");
    for (DeprecatedKeyDescriptor deprecatedKey : deprecatedKeys) {
      valueMap.keySet().stream()
          .filter(
              key ->
                  key.containsKey(deprecatedKey.getCompareKey())
                      || key.isBelow(new TigerConfigurationKey(deprecatedKey.getCompareKey())))
          .findFirst()
          .ifPresent(
              a -> {
                if (StringUtils.isNotEmpty(deprecatedKey.getNewKey())) {
                  joiner.add(
                      "The key ('"
                          + deprecatedKey.getDeprecatedKey()
                          + "') in yaml file should not be used anymore, use '"
                          + deprecatedKey.getNewKey()
                          + "' instead!");
                } else {
                  joiner.add(
                      "The key ('"
                          + deprecatedKey.getDeprecatedKey()
                          + "') in yaml file should not be used anymore! It is deprecated without a"
                          + " replacement!");
                }
              });
    }
    if (!joiner.toString().isEmpty()) {
      throw new TigerConfigurationException(joiner.toString());
    }
  }
}
