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
package de.gematik.test.tiger.testenvmgr.servers;

import static de.gematik.rbellogger.util.GlobalServerMap.addServerNameForPort;
import static de.gematik.test.tiger.common.SocketHelper.findFreePort;

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.TigerProxyApplication;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.tigerproxy_standalone.CfgStandaloneProxy;
import de.gematik.test.tiger.testenvmgr.env.TigerServerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.servers.log.TigerServerLogManager;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.util.*;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

@TigerServerType("tigerProxy")
public class TigerProxyServer extends AbstractExternalTigerServer {

  @Getter private ConfigurableApplicationContext applicationContext;
  @Getter private TigerProxy tigerProxyBean;

  public TigerProxyServer(
      TigerTestEnvMgr tigerTestEnvMgr, String serverId, CfgServer configuration) {
    super(serverId, configuration, tigerTestEnvMgr);
  }

  @Override
  public void assertThatConfigurationIsCorrect() {
    super.assertThatConfigurationIsCorrect();

    if (getConfiguration().getTigerProxyConfiguration() == null) {
      getConfiguration().setTigerProxyConfiguration(new TigerProxyConfiguration());
    }
    if (getConfiguration().getTigerProxyConfiguration().getAdminPort() <= 0) {
      getConfiguration().getTigerProxyConfiguration().setAdminPort(findFreePort());
    }
    if (getConfiguration().getTigerProxyConfiguration().getProxyPort() == null
        || getConfiguration().getTigerProxyConfiguration().getProxyPort() <= 0) {
      throw new TigerTestEnvException(
          "Missing proxy-port configuration for server '" + getServerId() + "'");
    }
  }

  @SneakyThrows
  @Override
  public void performStartup() {
    log.info("Entering pre-startup of tiger-proxy {}", getServerId());
    publishNewStatusUpdate(
        TigerServerStatusUpdate.builder()
            .statusMessage("Pre-start Tiger Proxy " + getServerId())
            .build());

    TigerProxyConfiguration tigerProxyConfiguration =
        getConfiguration().getTigerProxyConfiguration();
    tigerProxyConfiguration.setStandalone(false);
    CfgStandaloneProxy standaloneCfg = new CfgStandaloneProxy();
    standaloneCfg.setTigerProxy(tigerProxyConfiguration);
    if (tigerProxyConfiguration.getProxyRoutes() == null) {
      tigerProxyConfiguration.setProxyRoutes(new ArrayList<>());
    }

    if (tigerProxyConfiguration.getProxiedServer() != null) {
      getDestinationUrlFromProxiedServer(tigerProxyConfiguration);
    }
    if (StringUtils.isEmpty(tigerProxyConfiguration.getName())) {
      tigerProxyConfiguration.setName(getHostname());
    }

    tigerProxyConfiguration
        .getProxyRoutes()
        .forEach(
            route -> {
              route.setFrom(getTigerTestEnvMgr().replaceSysPropsInString(route.getFrom()));
              route.setTo(
                  Optional.ofNullable(route.getTo()).stream()
                      .flatMap(List::stream)
                      .map(getTigerTestEnvMgr()::replaceSysPropsInString)
                      .toList());
            });

    if (getTigerTestEnvMgr().isShuttingDown()) {
      log.debug("Skipping startup, already shutting down...");
      publishNewStatusUpdate(
          TigerServerStatusUpdate.builder()
              .statusMessage("Skipped startup of Tiger Proxy " + getServerId())
              .build());
      return;
    }

    Map<String, Object> properties = TigerTestEnvMgr.getConfiguredLoggingLevels();
    properties.putAll(TigerSerializationUtil.toMap(standaloneCfg));
    properties.put("spring.mustache.enabled", false); // TGR-875 avoid warning in console
    properties.put("spring.mustache.check-template-location", false);

    log.info("Actually performing startup of tiger-proxy {}", getServerId());
    statusMessage(
        "Starting Tiger Proxy "
            + getServerId()
            + " at "
            + tigerProxyConfiguration.getAdminPort()
            + "...");
    applicationContext =
        new SpringApplicationBuilder()
            .bannerMode(Mode.OFF)
            .properties(properties)
            .sources(TigerProxyApplication.class)
            .web(WebApplicationType.SERVLET)
            .registerShutdownHook(false)
            .initializers()
            .run();

    try {
      TigerServerLogManager.addProxyCustomerAppender(this);
    } catch (NoClassDefFoundError ncde) {
      log.warn(
          "Unable to detect logback library! Log appender for server {} not activated",
          getServerId());
    }

    waitForServerUp();
    publishNewStatusUpdate(
        TigerServerStatusUpdate.builder()
            .baseUrl(
                "http://localhost:"
                    + ((ServletWebServerApplicationContext) applicationContext)
                        .getWebServer()
                        .getPort()
                    + "/")
            .build());
    addServerNameForPort(tigerProxyConfiguration.getProxyPort(), this.getServerId());
    addServerNameForPort(tigerProxyConfiguration.getAdminPort(), this.getServerId());
    tigerProxyBean = applicationContext.getBean(TigerProxy.class);
  }

  @Override
  public void shutdown() {
    log.info("Stopping tiger proxy {}...", getServerId());
    if (applicationContext != null && applicationContext.isRunning()) {
      log.info("Triggering tiger-server shutdown for {}...", getServerId());
      getTigerProxy().close();
      applicationContext.stop();
      setStatus(TigerServerStatus.STOPPED, "Stopped Tiger Proxy " + getServerId());
    } else {
      log.info("Skipping tiger-server shutdown for {}!", getServerId());
      setStatus(TigerServerStatus.STOPPED, "Tiger Proxy " + getServerId() + " already stopped");
    }
  }

  private void getDestinationUrlFromProxiedServer(TigerProxyConfiguration cfg) {
    final String destUrl =
        getTigerTestEnvMgr().getServers().keySet().stream()
            .filter(srvid -> srvid.equals(cfg.getProxiedServer()))
            .findAny()
            .map(srvid -> getTigerTestEnvMgr().getServers().get(srvid))
            .map(srv -> srv.getDestinationUrl(cfg.getProxiedServerProtocol()))
            .orElseThrow(
                () ->
                    new TigerTestEnvException(
                        "Proxied server '" + cfg.getProxiedServer() + "' not found in list!"));

    TigerConfigurationRoute tigerRoute = new TigerConfigurationRoute();
    tigerRoute.setFrom("/");
    tigerRoute.setTo(Collections.singletonList(destUrl));
    cfg.getProxyRoutes().add(tigerRoute);
  }

  @Override
  public Optional<String> getHealthcheckUrl() {
    return Optional.of(
        "http://127.0.0.1:" + getConfiguration().getTigerProxyConfiguration().getAdminPort());
  }

  @Override
  boolean isHealthCheckNone() {
    return false;
  }

  public TigerProxy getTigerProxy() {
    return applicationContext.getBean(TigerProxy.class);
  }
}
