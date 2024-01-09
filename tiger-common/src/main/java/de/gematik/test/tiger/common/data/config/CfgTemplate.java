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

package de.gematik.test.tiger.common.data.config;

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CfgTemplate {

  private String templateName;

  private String type;

  private List<String> source = new ArrayList<>();
  private String version;
  private Integer startupTimeoutSec;
  private boolean active = true;
  private String healthcheckUrl;
  private Integer healthcheckReturnCode;
  private String logFile;

  private CfgExternalJarOptions externalJarOptions;
  private CfgDockerOptions dockerOptions = new CfgDockerOptions();
  private TigerProxyConfiguration tigerProxyCfg;
  private CfgHelmChartOptions helmChartOptions = new CfgHelmChartOptions();

  private final List<CfgKey> pkiKeys = new ArrayList<>();

  /** list of env vars to be set for docker, external Jar/TigerProxy */
  private List<String> environment = new ArrayList<>();

  /** mappings for local tiger proxy to be set when this server is active */
  private final List<String> urlMappings = new ArrayList<>();

  /**
   * properties to be exported to subsequent nodes as env vars and set as system properties to
   * current jvm
   */
  private final List<String> exports = new ArrayList<>();
}
