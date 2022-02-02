package de.gematik.test.tiger.testenvmgr.servers;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelFacet;
import de.gematik.test.tiger.common.data.config.CfgTigerProxyOptions;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import de.gematik.test.tiger.proxy.TigerProxyApplication;
import de.gematik.test.tiger.testenvmgr.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.tigerProxyStandalone.CfgStandaloneProxy;
import de.gematik.test.tiger.testenvmgr.config.tigerProxyStandalone.CfgStandaloneServer;
import java.util.List;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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
        final String destUrl;
        TigerServer proxiedServer = getTigerTestEnvMgr().getServers().keySet().stream()
            .filter(srvid -> srvid.equals(cfg.getProxiedServer()))
            .map(srvid -> getTigerTestEnvMgr().getServers().get(srvid))
            .findAny().orElseThrow(
                () -> new TigerTestEnvException(
                    "Proxied server '" + cfg.getProxiedServer() + "' not found in list!"));

        if (proxiedServer instanceof DockerServer) {
            if (proxiedServer.getConfiguration().getExternalJarOptions().getHealthcheck() == null) {
                if (proxiedServer.getStatus() != TigerServerStatus.RUNNING) {
                    throw new TigerTestEnvException("If reverse proxy is to be used with docker container '"
                        + proxiedServer.getHostname()
                        + "' make sure to start it first or have a valid healthcheck setting!");
                } else {
                    destUrl =
                        cfg.getProxyProtocol() + "://127.0.0.1:" + getConfiguration().getDockerOptions().getPorts()
                            .values()
                            .iterator().next();
                }
            } else {
                destUrl = proxiedServer.getConfiguration().getExternalJarOptions().getHealthcheck();
            }
        } else if (proxiedServer instanceof ExternalJarServer) {
            assertThat(proxiedServer.getConfiguration().getExternalJarOptions().getHealthcheck())
                .withFailMessage(
                    "To be proxied server '" + proxiedServer.getHostname() + "' has no valid healthcheck Url")
                .isNotBlank();
            destUrl = proxiedServer.getConfiguration().getExternalJarOptions().getHealthcheck();
        } else if (proxiedServer instanceof ExternalUrlServer) {
            assertThat(proxiedServer.getConfiguration().getSource())
                .withFailMessage(
                    "To be proxied server '" + proxiedServer.getHostname() + "' has no sources configured")
                .isNotEmpty();
            assertThat(proxiedServer.getConfiguration().getSource().get(0))
                .withFailMessage(
                    "To be proxied server '" + proxiedServer.getHostname() + "' has empty source[0] configured")
                .isNotBlank();
            destUrl = proxiedServer.getConfiguration().getSource().get(0);
        } else {
            throw new TigerTestEnvException(
                "Sophisticated reverse proxy for '" + proxiedServer.getClass().getSimpleName() + "' is not supported!");
        }

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
