/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.servers;

import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.TigerProxyApplication;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.tigerProxyStandalone.CfgStandaloneProxy;
import de.gematik.test.tiger.testenvmgr.env.TigerServerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.servers.log.TigerServerLogManager;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.SocketUtils;
import org.springframework.web.context.WebApplicationContext;

@TigerServerType("tigerProxy")
public class TigerProxyServer extends AbstractExternalTigerServer {

    private ConfigurableApplicationContext applicationContext;


    public TigerProxyServer(TigerTestEnvMgr tigerTestEnvMgr, String serverId, CfgServer configuration) {
        super(determineHostname(configuration, serverId), serverId, configuration, tigerTestEnvMgr);
    }

    public void assertThatConfigurationIsCorrect() {
        super.assertThatConfigurationIsCorrect();

        if (getConfiguration().getTigerProxyCfg() == null) {
            getConfiguration().setTigerProxyCfg(new TigerProxyConfiguration());
        }
        if (getConfiguration().getTigerProxyCfg().getAdminPort() <= 0) {
            getConfiguration().getTigerProxyCfg().setAdminPort(SocketUtils.findAvailableTcpPort());
        }
        if (getConfiguration().getTigerProxyCfg().getProxyPort() == null
            || getConfiguration().getTigerProxyCfg().getProxyPort() <= 0) {
            throw new TigerTestEnvException("Missing proxy-port configuration for server '" + getServerId() + "'");
        }
    }

    @Override
    public void performStartup() {
        log.info("Entering pre-startup of tiger-proxy {}", getServerId());
        publishNewStatusUpdate(TigerServerStatusUpdate.builder()
            .statusMessage("Pre-start Tiger Proxy " + getServerId())
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

        if (getTigerTestEnvMgr().isShuttingDown()) {
            log.debug("Skipping startup, already shutting down...");
            publishNewStatusUpdate(TigerServerStatusUpdate.builder()
                .statusMessage("Skipped startup of Tiger Proxy " + getServerId())
                .build());
            return;
        }

        log.info("Actually performing startup of tiger-proxy {}", getServerId());
        statusMessage("Starting Tiger Proxy " + getServerId() + " at " + reverseProxyCfg.getAdminPort() + "...");
        applicationContext = new SpringApplicationBuilder()
            .bannerMode(Mode.OFF)
            .properties(new HashMap<>(TigerSerializationUtil.toMap(standaloneCfg)))
            .sources(TigerProxyApplication.class)
            .web(WebApplicationType.SERVLET)
            .registerShutdownHook(false)
            .initializers()
            .run();

        TigerServerLogManager.addProxyCustomerAppender(this);

        waitForServerUp();
        publishNewStatusUpdate(TigerServerStatusUpdate.builder()
            .baseUrl("http://localhost:" + ((ServletWebServerApplicationContext)applicationContext).getWebServer().getPort() + "/webui")
            .build());
    }

    @Override
    public void shutdown() {
        log.info("Stopping tiger proxy {}...", getServerId());
        if (applicationContext != null
            && applicationContext.isRunning()) {
            log.info("Triggering tiger-server shutdown for {}...", getServerId());
            applicationContext.close();
            setStatus(TigerServerStatus.STOPPED, "Stopped Tiger Proxy " + getServerId());
        } else {
            log.info("Skipping tiger-server shutdown for {}!", getServerId());
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
    Optional<String> getHealthcheckUrl() {
        return Optional.of("http://127.0.0.1:" + getConfiguration().getTigerProxyCfg().getAdminPort());
    }

    @Override
    boolean isHealthCheckNone() {
        return false;
    }

    public TigerProxy getTigerProxy() {
        return applicationContext.getBean(TigerProxy.class);
    }
}
