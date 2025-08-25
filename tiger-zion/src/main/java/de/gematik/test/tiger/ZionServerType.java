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
package de.gematik.test.tiger;

import static de.gematik.rbellogger.util.GlobalServerMap.addServerNameForPort;

import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.env.TigerServerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.servers.AbstractExternalTigerServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerStatus;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerType;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.zion.ZionApplication;
import de.gematik.test.tiger.zion.config.ZionServerConfiguration;
import java.net.URL;
import java.util.HashMap;
import java.util.Optional;
import lombok.Getter;
import lombok.SneakyThrows;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

@TigerServerType("zion")
public class ZionServerType extends AbstractExternalTigerServer {

  @Getter private ConfigurableApplicationContext applicationContext;

  public ZionServerType(String serverId, CfgServer configuration, TigerTestEnvMgr tigerTestEnvMgr) {
    super(serverId, configuration, tigerTestEnvMgr);
  }

  @Override
  public Optional<String> getHealthcheckUrl() {
    return Optional.of("http://localhost:" + getServerPort());
  }

  @SneakyThrows
  @Override
  public void performStartup() {
    log.info("Entering pre-startup of Zion-Server {}", getServerId());
    publishNewStatusUpdate(
        TigerServerStatusUpdate.builder()
            .statusMessage("Pre-start Zion-Server " + getServerId())
            .build());
    final ZionServerConfiguration zionConfiguration = getZionConfiguration();

    final HashMap<String, Object> propertyMap =
        new HashMap<>(
            TigerSerializationUtil.toMap(zionConfiguration.getZionConfiguration(), "zion"));
    final int serverPort = getServerPort();
    propertyMap.put("server.port", serverPort);
    propertyMap.put("spring.mustache.enabled", false); // TGR-875 avoid warning in console
    propertyMap.put("spring.mustache.check-template-location", false);

    if (getTigerTestEnvMgr().isShuttingDown()) {
      log.debug("Skipping startup, already shutting down...");
      publishNewStatusUpdate(
          TigerServerStatusUpdate.builder()
              .statusMessage("Skipped startup of Zion-Server " + getServerId())
              .build());
      return;
    }

    log.info("Actually performing startup of Zion-Server {}", getServerId());
    statusMessage("Starting ZionServer " + getServerId() + " at " + serverPort + "...");
    applicationContext =
        new SpringApplicationBuilder()
            .bannerMode(Mode.OFF)
            .properties(propertyMap)
            .sources(ZionApplication.class)
            .web(WebApplicationType.SERVLET)
            .registerShutdownHook(false)
            .initializers()
            .run();

    waitForServerUp();

    addServerToLocalProxyRouteMap(new URL(getZionServerBaseUrl()));
    addServerNameForPort(serverPort, this.getServerId());

    publishNewStatusUpdate(
        TigerServerStatusUpdate.builder().baseUrl(getZionServerBaseUrl()).build());
  }

  private String getZionServerBaseUrl() {
    return "http://localhost:"
        + ((ServletWebServerApplicationContext) applicationContext).getWebServer().getPort();
  }

  private int getServerPort() {
    return getZionConfiguration().getZionConfiguration().getServerPort();
  }

  private ZionServerConfiguration getZionConfiguration() {
    final CfgServer configuration = getConfiguration();
    if (configuration instanceof ZionServerConfiguration zionServerConfiguration) {
      injectServerName(zionServerConfiguration, getHostname());
      injectLocalTigerProxyAddress(zionServerConfiguration);
      return zionServerConfiguration;
    } else {
      throw new TigerEnvironmentStartupException(
          "Unexpected configuration type. Expected ZionServerConfiguration but found "
              + configuration.getClass().getName());
    }
  }

  private void injectServerName(ZionServerConfiguration zionConfiguration, String hostname) {
    zionConfiguration.getZionConfiguration().setServerName(hostname);
  }

  private void injectLocalTigerProxyAddress(ZionServerConfiguration zionServerConfiguration) {
    getTigerTestEnvMgr()
        .getLocalTigerProxyOptional()
        .ifPresent(
            localTigerProxy -> {
              String proxyAddress = "localhost:" + localTigerProxy.getProxyPort();
              zionServerConfiguration.getZionConfiguration().setLocalTigerProxy(proxyAddress);
            });
  }

  @Override
  public void shutdown() {
    log.info("Stopping Zion-Server {}...", getServerId());
    if (applicationContext != null && applicationContext.isRunning()) {
      log.info("Triggering tiger-server shutdown for {}...", getServerId());
      applicationContext.close();
      setStatus(TigerServerStatus.STOPPED, "Stopped Zion-Server " + getServerId());
    } else {
      log.info("Skipping tiger-server shutdown for {}!", getServerId());
      setStatus(TigerServerStatus.STOPPED, "Zion-Server " + getServerId() + " already stopped");
    }
  }

  @Override
  public Class<? extends CfgServer> getConfigurationBeanClass() {
    return ZionServerConfiguration.class;
  }
}
