/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.servers;

import static de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr.HTTP;
import de.gematik.test.tiger.common.config.ServerType;
import de.gematik.test.tiger.common.data.config.CfgDockerOptions;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.env.TigerServerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import lombok.Builder;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;

public class DockerServer extends TigerServer {

    @Builder
    DockerServer(String serverId, CfgServer configuration, TigerTestEnvMgr tigerTestEnvMgr) {
        super(determineHostname(configuration, serverId), serverId, tigerTestEnvMgr, configuration);
    }

    @Override
    public void performStartup() {
        publishNewStatusUpdate(TigerServerStatusUpdate.builder()
            .type(ServerType.DOCKER)
            .statusMessage("Starting docker container for " + getServerId() + " from '" + getDockerSource() + "'")
            .build());

        getTigerTestEnvMgr().getDockerManager().startContainer(this);

        // add routes needed for each server to local docker proxy
        // ATTENTION only one route per server!
        if (getConfiguration().getDockerOptions().getPorts() != null
            && !getConfiguration().getDockerOptions().getPorts().isEmpty()) {
            addRoute(TigerRoute.builder()
                .from(HTTP + getHostname())
                .to(HTTP + "localhost:" + getConfiguration().getDockerOptions().getPorts().values().iterator().next())
                .build());
        }
        statusMessage("Docker container " + getServerId() + " started");
    }

    public String getDockerSource() {
        return getConfiguration().getSource().get(0);
    }

    public CfgDockerOptions getDockerOptions() {
        return getConfiguration().getDockerOptions();
    }

    @Override
    public void shutdown() {
        log.info("Stopping docker container {}...", getServerId());
        removeAllRoutes();
        getTigerTestEnvMgr().getDockerManager().stopContainer(this);
        setStatus(TigerServerStatus.STOPPED, "Docker container " + getServerId() + " stopped");
    }

    @Override
    public String getDestinationUrl(String fallbackProtocol) {
        try {
            final URIBuilder uriBuilder = new URIBuilder(guessAServerUrl()).setPath("");
            if (StringUtils.isNotEmpty(fallbackProtocol)) {
                uriBuilder.setScheme(fallbackProtocol);
            }
            return uriBuilder.build().toURL().toString();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new TigerEnvironmentStartupException("Unable to build destination URL", e);
        }
    }

    private String guessAServerUrl() {
        if (StringUtils.isNotEmpty(getConfiguration().getHealthcheckUrl())) {
            return getConfiguration().getHealthcheckUrl();
        } else {
            if (getStatus() != TigerServerStatus.RUNNING) {
                throw new TigerTestEnvException("If reverse proxy is to be used with docker container '"
                    + getServerId() + "' make sure to start it first or have a valid healthcheck setting!");
            } else {
                return "http://127.0.0.1:" + getConfiguration().getDockerOptions().getPorts().values()
                    .iterator().next();
            }
        }
    }
}
