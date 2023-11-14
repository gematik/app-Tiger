/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.servers;

import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.env.TigerServerStatusUpdate;
import java.net.URI;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import org.gaul.httpbin.HttpBin;

/**
 * A server type that starts a <a href="https://github.com/gaul/java-httpbin">httpbin server</a>.
 */
@TigerServerType("httpbin")
public class HttpBinServer extends AbstractExternalTigerServer {

  private HttpBin httpbin;

  public HttpBinServer(TigerTestEnvMgr tigerTestEnvMgr, String serverId, CfgServer configuration) {
    super(serverId, serverId, configuration, tigerTestEnvMgr);
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

    var httpBinEndpoint = new URI("http://localhost:" + getServerport());
    httpbin = new HttpBin(httpBinEndpoint);
    httpbin.start();

    waitForServerUp();
    addServerToLocalProxyRouteMap(httpBinEndpoint.toURL());
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

  private int getServerport() {
    return getHttbBinConfiguration().getServerPort();
  }

  @SneakyThrows
  @Override
  public void shutdown() {
    log.info("Stopping HttpBin-Server {}...", getServerId());
    if (httpbin != null) {
      httpbin.stop();
    }
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
