/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.data.config;

import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
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
