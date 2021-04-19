package de.gematik.test.tiger.testenvmgr;

import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import java.io.File;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class TigerTestEnvMgr implements ITigerTestEnvMgr {

    private final Configuration configuration;

    public TigerTestEnvMgr() {
        final File cfgFile = new File(Optional.ofNullable(System.getenv("TIGER_TESTENV_CFGFILE"))
            .orElse(System.getProperty("TIGER_TESTENV_CFGFILE", "tiger-testenv.yaml")));
        configuration = new Configuration();
        configuration.readConfig(cfgFile);

        // TODO load default settings from classpath resource
    }

    @Override
    public void setUpEnvironment() {
        configuration.getServers().forEach(server -> {
            start(server);
        });
        log.info("finished set up test environemnt OK");
    }


    @Override
    public void start(final CfgServer server) {
        //TODO merge server cfg object with default values
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
        // TODO use docker api lib and instantiate container with given name and version
        loadPKIForServer(srv);
        configureProxyForServer(srv);
    }

    public void initializeExternal(final CfgServer srv) {
        log.info("starting external instance " + srv.getName() + "...");
        loadPKIForServer(srv);
        configureProxyForServer(srv);
        log.info("  Checking external instance  " + srv.getName() + " is available ...");
        // TODO
    }

    @Override
    public void shutDown(final CfgServer server) {
        //TODO merge server cfg object with default values
        final String[] uri = server.getInstanceUri().split(":");
        if (uri[0].equals("docker")) {
            shutDownDocker(server);
        } else if (uri[0].equals("external")) {
            shutDownExternal(server);
        } else {
            throw new TigerTestEnvException(
                String.format("Unsupported server type %s found in server %s", uri[0], server.getName()));
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

    private void shutDownDocker(final CfgServer srv) {

    }

    private void shutDownExternal(final CfgServer srv) {

    }
}
