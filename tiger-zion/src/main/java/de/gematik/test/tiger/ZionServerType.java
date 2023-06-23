package de.gematik.test.tiger;

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
import java.util.HashMap;
import java.util.Optional;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

@TigerServerType("zion")
public class ZionServerType extends AbstractExternalTigerServer {

    private ConfigurableApplicationContext applicationContext;

    public ZionServerType(TigerTestEnvMgr tigerTestEnvMgr, String serverId, CfgServer configuration) {
        super(serverId, serverId, configuration, tigerTestEnvMgr);
    }

    @Override
    public Optional<String> getHealthcheckUrl() {
        return Optional.of("http://localhost:" + getServerPort());
    }

    @Override
    public void performStartup() {
        log.info("Entering pre-startup of Zion-Server {}", getServerId());
        publishNewStatusUpdate(TigerServerStatusUpdate.builder()
            .statusMessage("Pre-start Zion-Server " + getServerId())
            .build());
        final ZionServerConfiguration zionConfiguration = getZionConfiguration();
        final HashMap<String, Object> propertyMap
            = new HashMap<>(TigerSerializationUtil.toMap(zionConfiguration.getZionConfiguration(), "zion"));
        final int serverPort = getServerPort();
        propertyMap.put("server.port", serverPort);

        if (getTigerTestEnvMgr().isShuttingDown()) {
            log.debug("Skipping startup, already shutting down...");
            publishNewStatusUpdate(TigerServerStatusUpdate.builder()
                .statusMessage("Skipped startup of Zion-Server " + getServerId())
                .build());
            return;
        }

        log.info("Actually performing startup of Zion-Server {}", getServerId());
        statusMessage("Starting ZionServer " + getServerId() + " at " + serverPort + "...");
        applicationContext = new SpringApplicationBuilder()
            .bannerMode(Mode.OFF)
            .properties(propertyMap)
            .sources(ZionApplication.class)
            .web(WebApplicationType.SERVLET)
            .registerShutdownHook(false)
            .initializers()
            .run();

        waitForServerUp();
        publishNewStatusUpdate(TigerServerStatusUpdate.builder()
            .baseUrl("http://localhost:" + ((ServletWebServerApplicationContext) applicationContext).getWebServer().getPort())
            .build());
    }

    private int getServerPort() {
        return getZionConfiguration().getZionConfiguration().getServerPort();
    }


    private ZionServerConfiguration getZionConfiguration() {
        final CfgServer configuration = getConfiguration();
        if (configuration instanceof ZionServerConfiguration zionServerConfiguration) {
            return zionServerConfiguration;
        } else {
            throw new TigerEnvironmentStartupException("Unexpected configuration type. Expected ZionServerConfiguration but found " +
                    configuration.getClass().getName());
        }
    }

    @Override
    public void shutdown() {
        log.info("Stopping Zion-Server {}...", getServerId());
        if (applicationContext != null
            && applicationContext.isRunning()) {
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
