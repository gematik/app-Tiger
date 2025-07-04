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

import de.gematik.test.tiger.EmbeddedHttpbin;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.env.TigerServerStatusUpdate;
import java.net.URI;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;

/**
 * A server type that starts a <a href="https://github.com/gaul/java-httpbin">httpbin server</a>.
 */
@TigerServerType("httpbin")
public class HttpBinServer extends AbstractExternalTigerServer {

  private EmbeddedHttpbin httpbin;

  public HttpBinServer(TigerTestEnvMgr tigerTestEnvMgr, String serverId, CfgServer configuration) {
    super(serverId, configuration, tigerTestEnvMgr);
  }

  @SneakyThrows
  @Override
  public void performStartup() {
    log.info("Pre-startup of HttpBin server {}", getServerId());
    publishNewStatusUpdate(
        TigerServerStatusUpdate.builder()
            .statusMessage("Pre-start HttpBin-Server " + getServerId())
            .build());

    if (getTigerTestEnvMgr().isShuttingDown()) {
      log.debug("Skipping startup, already shutting down...");
      publishNewStatusUpdate(
          TigerServerStatusUpdate.builder()
              .statusMessage("Skipped startup of HttpBin-Server " + getServerId())
              .build());
      return;
    }

    log.info("Actually performing startup of HttpBin-Server {}", getServerId());

    var serverPort = getServerPort();

    httpbin = new EmbeddedHttpbin(serverPort, true);
    httpbin.start();

    waitForServerUp();
    var httpBinEndpoint = new URI("http://localhost:" + serverPort);
    addServerToLocalProxyRouteMap(httpBinEndpoint.toURL());
    addServerNameForPort(serverPort, this.getServerId());
  }

  private HttpBinConfiguration getHttbBinConfiguration() {
    CfgServer configuration = getConfiguration();
    if (configuration instanceof HttpBinConfiguration httpBinConfiguration) {
      return httpBinConfiguration;
    } else {
      throw new TigerConfigurationException(
          "Unexpected configuration type. Expected %s but found %s"
              .formatted(HttpBinConfiguration.class.getName(), configuration.getClass().getName()));
    }
  }

  private int getServerPort() {
    return getHttbBinConfiguration().getServerPort();
  }

  @SneakyThrows
  @Override
  public void shutdown() {
    log.info("Stopping HttpBin-Server {}...", getServerId());
    if (httpbin != null) {
      httpbin.stop();
    }
    setStatus(TigerServerStatus.STOPPED, "HttpBin-server " + getServerId() + " stopped");
  }

  @Override
  public Class<? extends CfgServer> getConfigurationBeanClass() {
    return HttpBinConfiguration.class;
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class HttpBinConfiguration extends CfgServer {
    private int serverPort;
  }
}
