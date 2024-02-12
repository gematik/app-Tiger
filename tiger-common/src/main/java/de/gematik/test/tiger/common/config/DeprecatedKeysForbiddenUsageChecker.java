/*
 * Copyright (c) 2024 gematik GmbH
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

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** A specialized class that checks for old/deprecated keys. */
public final class DeprecatedKeysForbiddenUsageChecker {

  private static final List<DeprecatedKeyDescriptor> deprecatedKeysMap =
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
              .newKey("")
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
              .newKey("activateEpaVauAnalysis")
              .build(),
          DeprecatedKeyDescriptor.builder()
              .compareKey("tiger.servers.*.tigerproxyconfiguration.proxycfg.activateVauAnalysis")
              .deprecatedKey("activateVauAnalysis")
              .newKey("activateEpaVauAnalysis")
              .build(),
          DeprecatedKeyDescriptor.builder()
              .compareKey("tiger.servers.*.tigerproxyconfiguration")
              .deprecatedKey("tigerProxyCfg")
              .newKey("tigerProxyConfiguration")
              .build());

  private DeprecatedKeysForbiddenUsageChecker() {}

  public static void checkForDeprecatedKeys(Map<TigerConfigurationKey, String> valueMap)
      throws TigerConfigurationException {
    if (valueMap == null) {
      throw new TigerConfigurationException("Tiger configuration map is null!");
    }
    StringJoiner joiner = new StringJoiner("\n");
    for (DeprecatedKeyDescriptor deprecatedKey : deprecatedKeysMap) {
      valueMap.keySet().stream()
          .filter(key -> key.containsKey(deprecatedKey.getCompareKey()))
          .findFirst()
          .ifPresent(
              a -> {
                if (!deprecatedKey.getNewKey().isEmpty()) {
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
                          + "') in yaml file should not be used anymore, it is omitted!");
                }
              });
    }
    if (!joiner.toString().isEmpty()) {
      throw new TigerConfigurationException(joiner.toString());
    }
  }
}
