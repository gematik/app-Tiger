/*
 * Copyright 2021-2026 gematik GmbH
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import org.junit.jupiter.api.Test;

class TigerProxyNamingTest {

  @Test
  void proxyWithoutNameShouldReceiveRandomlyAssignedName() {
    try (TigerProxy proxy = new TigerProxy(TigerProxyConfiguration.builder().build())) {
      assertThat(proxy.proxyName()).isNotBlank().matches("\\w+-\\w+");
    }
  }

  @Test
  void differentConfigurationsShouldGenerateDifferentNames() {
    try (TigerProxy proxy1 =
            new TigerProxy(TigerProxyConfiguration.builder().proxyPort(8080).build());
        TigerProxy proxy2 =
            new TigerProxy(TigerProxyConfiguration.builder().proxyPort(8081).build())) {

      assertThat(proxy1.proxyName()).isNotEqualTo(proxy2.proxyName());
    }
  }

  @Test
  void sameConfigurationShouldGenerateSameName() {
    TigerProxyConfiguration config1 = TigerProxyConfiguration.builder().proxyPort(9090).build();
    TigerProxyConfiguration config2 = TigerProxyConfiguration.builder().proxyPort(9090).build();

    String name1;
    try (TigerProxy proxy1 = new TigerProxy(config1)) {
      name1 = proxy1.proxyName();
    }

    String name2;
    try (TigerProxy proxy2 = new TigerProxy(config2)) {
      name2 = proxy2.proxyName();
    }

    assertThat(name1).isEqualTo(name2);
  }
}
