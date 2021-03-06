/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.testenvmgr.servers;

import de.gematik.test.tiger.common.config.ServerType;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.TigerProxyApplication;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.tigerProxyStandalone.CfgStandaloneProxy;
import de.gematik.test.tiger.testenvmgr.env.TigerServerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class TigerProxyServer extends AbstractExternalTigerServer {

    private ConfigurableApplicationContext applicationContext;

    TigerProxyServer(String serverId, CfgServer configuration, TigerTestEnvMgr tigerTestEnvMgr) {
        super(determineHostname(configuration, serverId), serverId, configuration, tigerTestEnvMgr);
    }

    @Override
    public void performStartup() {
        publishNewStatusUpdate(TigerServerStatusUpdate.builder()
            .type(ServerType.TIGERPROXY)
            .statusMessage("Ppre-start Tiger Proxy " + getServerId())
            .build());

        TigerProxyConfiguration reverseProxyCfg = getConfiguration().getTigerProxyCfg();
        CfgStandaloneProxy standaloneCfg = new CfgStandaloneProxy();
        standaloneCfg.setTigerProxy(reverseProxyCfg);
        if (reverseProxyCfg.getProxyRoutes() == null) {
            reverseProxyCfg.setProxyRoutes(new ArrayList<>());
        }

        if (reverseProxyCfg.getProxiedServer() != null) {
            getDestinationUrlFromProxiedServer(reverseProxyCfg);
        }

        reverseProxyCfg.getProxyRoutes().forEach(route -> {
            route.setFrom(getTigerTestEnvMgr().replaceSysPropsInString(route.getFrom()));
            route.setTo(getTigerTestEnvMgr().replaceSysPropsInString(route.getTo()));
        });

        statusMessage("Starting Tiger Proxy " + getServerId() + " at " + reverseProxyCfg.getAdminPort() + "...");
        applicationContext = new SpringApplicationBuilder()
            .bannerMode(Mode.OFF)
            .properties(new HashMap<>(TigerSerializationUtil.toMap(standaloneCfg)))
            .sources(TigerProxyApplication.class)
            .web(WebApplicationType.SERVLET)
            .registerShutdownHook(false)
            .initializers()
            .run();

        waitForService(true);
        if (getStatus() == TigerServerStatus.STARTING) {
            waitForService(false);
        }
        statusMessage("Tiger Proxy " + getServerId() + " started");
    }

    @Override
    public void shutdown() {
        if (applicationContext != null
            && applicationContext.isRunning()) {
            applicationContext.close();
            setStatus(TigerServerStatus.STOPPED, "Stopped Tiger Proxy " + getServerId());
        } else {
            setStatus(TigerServerStatus.STOPPED, "Tiger Proxy " + getServerId() + " already stopped");
        }
    }

    private void getDestinationUrlFromProxiedServer(TigerProxyConfiguration cfg) {
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
        cfg.getProxyRoutes().add(tigerRoute);
    }

    @Override
    String getHealthcheckUrl() {
        return "http://127.0.0.1:" + getConfiguration().getTigerProxyCfg().getAdminPort();
    }

    @Override
    boolean isHealthCheckNone() {
        return false;
    }

    public TigerProxy getTigerProxy() {
        return applicationContext.getBean(TigerProxy.class);
    }
}
