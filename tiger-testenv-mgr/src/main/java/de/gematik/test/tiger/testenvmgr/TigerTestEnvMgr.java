package de.gematik.test.tiger.testenvmgr;

import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import java.io.File;
import java.net.ServerSocket;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class TigerTestEnvMgr implements ITigerTestEnvMgr {

    private final Configuration configuration;

    private final DockerMgr dockerManager;

    @SneakyThrows
    public TigerTestEnvMgr() {
        // read configuration from file and templates from classpath resource
        final File cfgFile = new File(Optional.ofNullable(System.getenv("TIGER_TESTENV_CFGFILE"))
            .orElse(System.getProperty("TIGER_TESTENV_CFGFILE", "tiger-testenv.yaml")));
        configuration = new Configuration();
        configuration.readConfig(cfgFile.toURI());
        final Configuration templates = new Configuration();
        templates.readConfig(Objects.requireNonNull(getClass().getResource("/templates.yaml")).toURI());

        // apply templates to read in configuration
        log.info("applying server templates");
        configuration.getTemplates().addAll(templates.getTemplates());
        configuration.applyTemplates();

        dockerManager = new DockerMgr();
    }

    @Override
    public void setUpEnvironment() {
        log.info("starting set up of test environment...");
        configuration.getServers().forEach(this::start);
        log.info("finished set up test environment OK");
    }

    @Override
    public void composeEnvironment(final File composeFile) {
        // TODO
    }

    @Override
    public List<CfgServer> getTestEnvironment() {
        return configuration.getServers();
    }

    @Override
    public void start(final CfgServer server) {
        final String[] uri = server.getInstanceUri().split(":");
        if (uri[0].equals("docker")) {
            startDocker(server);
        } else if (uri[0].equals("external")) {
            initializeExternal(server);
        } else {
            throw new TigerTestEnvException(
                String.format("Unsupported server type %s found in server %s", uri[0], server.getName()));
        }
    }

    private void startDocker(final CfgServer srv) {
        log.info("starting docker instance " + srv.getName() + "...");
        dockerManager.startContainer(srv);
        loadPKIForServer(srv);
        configureProxyForServer(srv);
    }

    public void initializeExternal(final CfgServer srv) {
        log.info("starting external instance " + srv.getName() + "...");
        loadPKIForServer(srv);
        configureProxyForServer(srv);
        log.info("  Checking external instance  " + srv.getName() + " is available ...");
        // TODO check availability
    }

    @Override
    public void shutDown(final CfgServer server) {
        final String[] uri = server.getInstanceUri().split(":");
        // uri[0].equals("docker") nothing to do as testcontainers is cleaning up after run automatically (see ryuk)
        if (uri[0].equals("external")) {
            shutDownExternal(server);
        }
    }

    private void configureProxyForServer(final CfgServer srv) {
        log.info("  initializing proxy for instance " + srv.getName() + "...");
        // TODO send url mappings and keys
    }

    private void loadPKIForServer(final CfgServer srv) {
        log.info("  loading PKI resources for instance " + srv.getName() + "...");
        // TODO initialize pki cert and key pool either from local folder
        //  if env TIGER_TESTENV_PKIFOLDER is set
        // or from classpath in resources/pki
    }

    private void shutDownDocker(final CfgServer server) {
        dockerManager.stopContainer(server);
    }

    private void shutDownExternal(final CfgServer srv) {
        // TODO tell proxy to drop this route
    }

    @SneakyThrows
    private Integer getFreePort() {
        try (final ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
