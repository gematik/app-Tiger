/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.servers;

import de.gematik.test.tiger.common.config.ServerType;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.env.TigerServerStatusUpdate;
import java.net.MalformedURLException;
import java.net.URL;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class ExternalUrlServer extends AbstractExternalTigerServer {

    @Builder
    ExternalUrlServer(String serverId, CfgServer configuration, TigerTestEnvMgr tigerTestEnvMgr) {
        super(determineHostname(configuration, serverId), serverId, configuration, tigerTestEnvMgr);
    }

    @Override
    public void performStartup() {
        publishNewStatusUpdate(TigerServerStatusUpdate.builder()
            .type(ServerType.EXTERNALURL)
            .statusMessage("Starting external URL instance " + getServerId() + "...")
            .build());

        final var url = buildUrl();
        addServerToLocalProxyRouteMap(url);
        publishNewStatusUpdate(TigerServerStatusUpdate.builder()
            .baseUrl(extractBaseUrl(url))
            .build());

        log.info("  Waiting 50% of start up time for external URL instance  {} to come up ...", getServerId());
        waitForService(true);
        if (getStatus() == TigerServerStatus.STARTING) {
            waitForService(false);
        }
    }

    @Override
    public void shutdown() {
        removeAllRoutes();
        setStatus(TigerServerStatus.STOPPED, "Disconnected external url " + getServerId());
    }

    @Override
    String getHealthcheckUrl() {
        if (getConfiguration().getExternalJarOptions() == null
        || StringUtils.isEmpty(getConfiguration().getHealthcheckUrl())){
            return getConfiguration().getSource().get(0);
        } else {
            return getConfiguration().getHealthcheckUrl();
        }
    }

    private URL buildUrl() {
        try {
            return new URL(getTigerTestEnvMgr().replaceSysPropsInString(getConfiguration().getSource().get(0)));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Could not parse source URL '" + getConfiguration().getSource().get(0) + "'!", e);
        }
    }
}
