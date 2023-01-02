/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.servers;

import static de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr.HTTP;
import de.gematik.test.tiger.common.config.SourceType;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.CfgDockerOptions;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.env.DockerMgr;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import lombok.Builder;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;

@TigerServerType("docker")
public class DockerServer extends AbstractTigerServer {

    public static final DockerMgr dockerManager = new DockerMgr();

    @Builder
    public DockerServer(TigerTestEnvMgr tigerTestEnvMgr, String serverId, CfgServer configuration) {
        super(determineHostname(configuration, serverId), serverId, tigerTestEnvMgr, configuration);
    }

    @Override
    public void assertThatConfigurationIsCorrect() {
        super.assertThatConfigurationIsCorrect();

        assertCfgPropertySet(getConfiguration(), "version");
        assertCfgPropertySet(getConfiguration(), "source");
    }

    @Override
    public void performStartup() {
        statusMessage("Starting docker container for " + getServerId() + " from '" + getDockerSource() + "'");
        dockerManager.startContainer(this);

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

    @Override
    protected void processExports() {
        super.processExports();

        if (getConfiguration().getDockerOptions().getPorts() != null
            && !getConfiguration().getDockerOptions().getPorts().isEmpty()) {
            getConfiguration().getExports().forEach(exp -> {
                String[] kvp = exp.split("=", 2);
                String origValue = TigerGlobalConfiguration.readString(kvp[0]);
                kvp[1] = origValue;
                // ports substitution are only supported for docker based instances
                if (getConfiguration().getDockerOptions().getPorts() != null) {
                    getConfiguration().getDockerOptions().getPorts().forEach((localPort, externPort) ->
                        kvp[1] = kvp[1].replace("${PORT:" + localPort + "}", String.valueOf(externPort))
                    );
                }
                if (!origValue.equals(kvp[1])) {
                    log.info("Setting global property {}={}", kvp[0], kvp[1]);
                    TigerGlobalConfiguration.putValue(kvp[0], kvp[1], SourceType.RUNTIME_EXPORT);
                }
            });
        }
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
        dockerManager.stopContainer(this);
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
