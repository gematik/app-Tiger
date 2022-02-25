/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.servers;

import de.gematik.test.tiger.common.data.config.CfgTigerProxyOptions;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import de.gematik.test.tiger.proxy.TigerProxyApplication;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.tigerProxyStandalone.CfgStandaloneProxy;
import de.gematik.test.tiger.testenvmgr.config.tigerProxyStandalone.CfgStandaloneServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@Slf4j
public class TigerProxyServer extends AbstractExternalTigerServer {

    private ConfigurableApplicationContext applicationContext;

    TigerProxyServer(String serverId, CfgServer configuration, TigerTestEnvMgr tigerTestEnvMgr) {
        super(determineHostname(configuration, serverId), serverId, configuration, tigerTestEnvMgr);
    }

    @Override
    public void performStartup() {
        CfgTigerProxyOptions reverseProxyCfg = getConfiguration().getTigerProxyCfg();

        CfgStandaloneProxy standaloneCfg = new CfgStandaloneProxy();
        standaloneCfg.setServer(new CfgStandaloneServer());
        standaloneCfg.getServer().setPort(reverseProxyCfg.getServerPort());

        standaloneCfg.setTigerProxy(reverseProxyCfg.getProxyCfg());
        if (reverseProxyCfg.getProxyCfg().getProxyRoutes() == null) {
            reverseProxyCfg.getProxyCfg().setProxyRoutes(new ArrayList<>());
        }

        if (reverseProxyCfg.getProxiedServer() != null) {
            getDestinationUrlFromProxiedServer(reverseProxyCfg);
        }

        reverseProxyCfg.getProxyCfg().getProxyRoutes().forEach(route -> {
            route.setFrom(getTigerTestEnvMgr().replaceSysPropsInString(route.getFrom()));
            route.setTo(getTigerTestEnvMgr().replaceSysPropsInString(route.getTo()));
        });

        Map<String, Object> properties = new HashMap<>();
        properties.put("server.port", reverseProxyCfg.getServerPort());
        properties.putAll(TigerSerializationUtil.toMap(standaloneCfg));

        applicationContext = new SpringApplicationBuilder()
            .properties(properties)
            .sources(TigerProxyApplication.class)
            .web(WebApplicationType.SERVLET)
            .initializers()
            .run();

        waitForService(true);
        if (getStatus() == TigerServerStatus.STARTING) {
            waitForService(false);
        }
    }

    @Override
    public void shutdown() {
        if (applicationContext != null
            && applicationContext.isRunning()) {
            applicationContext.stop();
        }
    }

    private void getDestinationUrlFromProxiedServer(CfgTigerProxyOptions cfg) {
        final String destUrl = getTigerTestEnvMgr().getServers().keySet().stream()
            .filter(srvid -> srvid.equals(cfg.getProxiedServer()))
            .findAny()
            .map(srvid -> getTigerTestEnvMgr().getServers().get(srvid))
            .map(srv -> srv.getDestinationUrl(cfg.getProxiedServerProtocol()))
            .orElseThrow(
                () -> new TigerTestEnvException(
                    "Proxied server '" + cfg.getProxiedServer() + "' not found in list!"));

        TigerRoute tigerRoute = new TigerRoute();
        tigerRoute.setFrom("/");
        tigerRoute.setTo(destUrl);
        cfg.getProxyCfg().getProxyRoutes().add(tigerRoute);
    }

    @Override
    String getHealthcheckUrl() {
        return "http://127.0.0.1:" + getConfiguration().getTigerProxyCfg().getServerPort();
    }

    @Override
    boolean isHealthCheckNone() {
        return false;
    }
}
