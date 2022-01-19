package de.gematik.test.tiger.testenvmgr.servers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.gematik.test.tiger.common.config.CfgTigerProxyOptions;
import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.testenvmgr.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.tigerProxyStandalone.CfgStandaloneProxy;
import de.gematik.test.tiger.testenvmgr.config.tigerProxyStandalone.CfgStandaloneServer;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class TigerProxyServer extends ExternalJarServer {

    TigerProxyServer(String serverId, CfgServer configuration, TigerTestEnvMgr tigerTestEnvMgr) {
        super(serverId, configuration, tigerTestEnvMgr);
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

        final String downloadUrl;
        final String jarFile = "tiger-standalone-proxy-" + getConfiguration().getVersion() + ".jar";
        if (getConfiguration().getSource().get(0).equals("nexus")) {
            downloadUrl =
                "https://build.top.local/nexus/service/local/repositories/releases/content/de/gematik/test/tiger-standalone-proxy/"
                    + getConfiguration().getVersion() + "/" + jarFile;
        } else if (getConfiguration().getSource().get(0).equals("maven")) {
            downloadUrl = "https://repo1.maven.org/maven2/de/gematik/test/tiger-standalone-proxy/"
                + getConfiguration().getVersion() + "/" + jarFile;
        } else {
            downloadUrl = getConfiguration().getSource().get(0);
        }
        final File folder = new File(getConfiguration().getExternalJarOptions().getWorkingDir());
        getConfiguration().setSource(List.of(downloadUrl));
        if (getConfiguration().getExternalJarOptions().getHealthcheck() == null) {
            getConfiguration().getExternalJarOptions().setHealthcheck("http://127.0.0.1:" + reverseProxyCfg.getServerPort());
        }
        if (getConfiguration().getExternalJarOptions().getArguments() == null) {
            getConfiguration().getExternalJarOptions().setArguments(new ArrayList<>());
        }
        getConfiguration().getExternalJarOptions().getArguments().add("--spring.profiles.active=" + getHostname());

        writeTigerConfigurationToYamlFile(standaloneCfg, folder);

        super.performStartup();
    }

    private void writeTigerConfigurationToYamlFile(CfgStandaloneProxy standaloneCfg, File folder) {
        try {
            ObjectMapper om = new ObjectMapper(new YAMLFactory());
            om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            om.writeValue(Path.of(folder.getAbsolutePath(), "application-" + getHostname() + ".yaml").toFile(),
                standaloneCfg);
        } catch (IOException e) {
            throw new TigerEnvironmentStartupException("Error while writing Tiger-Proxy YAML-File for " + getHostname(), e);
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
                        cfg.getProxyProtocol() + "://127.0.0.1:" + getConfiguration().getDockerOptions().getPorts().values()
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
}
