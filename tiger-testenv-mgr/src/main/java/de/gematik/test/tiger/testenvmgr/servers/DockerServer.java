package de.gematik.test.tiger.testenvmgr.servers;

import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.data.config.CfgDockerOptions;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import static de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr.HTTP;

@Slf4j
public class DockerServer extends TigerServer {

    @Builder
    DockerServer(String serverId, CfgServer configuration, TigerTestEnvMgr tigerTestEnvMgr) {
        super(determineHostname(configuration, serverId), serverId, tigerTestEnvMgr, configuration);
    }

    @Override
    public void performStartup() {
        log.info(Ansi.colorize("Starting docker container for {} :{}", RbelAnsiColors.GREEN_BOLD),
            getHostname(), getDockerSource());
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
        log.info(Ansi.colorize("Docker container Startup for {} : {} OK", RbelAnsiColors.GREEN_BOLD),
            getHostname(), getDockerSource());
    }

    public String getDockerSource() {
        return getConfiguration().getSource().get(0);
    }

    public CfgDockerOptions getDockerOptions() {
        return getConfiguration().getDockerOptions();
    }

    @Override
    public void shutdown() {
        log.info("Stopping docker container {}...", getHostname());
        removeAllRoutes();
        getTigerTestEnvMgr().getDockerManager().stopContainer(this);
    }
}
