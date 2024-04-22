/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

public class TigerConfigurationKeys {

  private TigerConfigurationKeys() {}

  public static final TigerTypedConfigurationKey<Integer> TESTENV_MGR_RESERVED_PORT =
      new TigerTypedConfigurationKey<>("tiger.lib.workflowUiPort", Integer.class);
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
  public static final TigerTypedConfigurationKey<Boolean> TRAFFIC_VISUALIZATION_ACTIVE =
      new TigerTypedConfigurationKey<>("tiger.lib.trafficVisualization", Boolean.class, false);
}
