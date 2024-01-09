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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

public class TigerConfigurationKeys {

  private TigerConfigurationKeys() {}

  public static final TigerTypedConfigurationKey<Integer> TESTENV_MGR_RESERVED_PORT =
      new TigerTypedConfigurationKey<>("tiger.internal.testenvmgr.port", Integer.class);
  public static final TigerTypedConfigurationKey<Integer> LOCALPROXY_ADMIN_RESERVED_PORT =
      new TigerTypedConfigurationKey<>("tiger.internal.localproxy.admin.port", Integer.class);

  public static final TigerTypedConfigurationKey<Integer> LOCAL_PROXY_ADMIN_PORT =
      new TigerTypedConfigurationKey<>("tiger.tigerProxy.adminPort", Integer.class);
  public static final TigerTypedConfigurationKey<Integer> LOCAL_PROXY_PROXY_PORT =
      new TigerTypedConfigurationKey<>("tiger.tigerProxy.proxyPort", Integer.class);
  public static final TigerTypedConfigurationKey<Boolean> SKIP_ENVIRONMENT_SETUP =
      new TigerTypedConfigurationKey<>("tiger.skipEnvironmentSetup", Boolean.class, false);
  public static final TigerTypedConfigurationKey<Boolean> SHOW_TIGER_LOGO =
      new TigerTypedConfigurationKey<>("tiger.logo", Boolean.class, false);
  public static final TigerTypedConfigurationKey<String> TIGER_YAML_VALUE =
      new TigerTypedConfigurationKey<>("tiger.yaml", String.class);
  public static final TigerTypedConfigurationKey<String> TIGER_TESTENV_CFGFILE_LOCATION =
      new TigerTypedConfigurationKey<>("tiger.testenv.cfgfile", String.class);
  public static final TigerTypedConfigurationKey<Integer> EXTERNAL_SERVER_CONNECTION_TIMEOUT =
      new TigerTypedConfigurationKey<>(
          "tiger.internal.externalServer.connectionTimeout", Integer.class, 1000);

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static class ExperimentalFeatures {
    public static final TigerTypedConfigurationKey<Boolean> TRAFFIC_VISUALIZATION_ACTIVE =
        new TigerTypedConfigurationKey<>(
            "tiger.lib.experimental.trafficVisualization", Boolean.class, false);
  }
}
