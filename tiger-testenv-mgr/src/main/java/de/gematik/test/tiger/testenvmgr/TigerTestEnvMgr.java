package de.gematik.test.tiger.testenvmgr;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.common.OSEnvironment;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.configuration.ForwardProxyInfo;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import java.io.File;
import java.net.ServerSocket;
import java.util.*;
import java.util.Map.Entry;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class TigerTestEnvMgr implements ITigerTestEnvMgr {

    private final Configuration configuration;

    private final DockerMgr dockerManager;

    private final Map<String, Object> environmentVariables;

    private final TigerProxy localDockerProxy;

    @SneakyThrows
    public TigerTestEnvMgr() {
        // read configuration from file and templates from classpath resource
        final File cfgFile = new File(OSEnvironment.getAsString(
            "TIGER_TESTENV_CFGFILE", "tiger-testenv.yaml"));
        configuration = new Configuration();
        configuration.readConfig(cfgFile.toURI());
        final Configuration templates = new Configuration();
        templates.readConfig(Objects.requireNonNull(getClass().getResource("/templates.yaml")).toURI());

        // apply templates to read in configuration
        log.info("applying server templates");
        configuration.getTemplates().addAll(templates.getTemplates());
        configuration.applyTemplates();

        environmentVariables = new HashMap<>();

        dockerManager = new DockerMgr();

        localDockerProxy = new TigerProxy(
            TigerProxyConfiguration.builder()
                .forwardToProxy(ForwardProxyInfo.builder().hostname("192.168.230.85").port(3128).build())
                .proxyRoutes(Collections.emptyMap())
                .build());
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

        if (server.isActive()) {

            // TODO if proxy env are in imports replace  with localdockerproxy data
            if (uri[0].equals("docker")) {
                final List<String> imports = server.getImports();
                for (int i = 0; i < imports.size(); i++) {
                    imports.set(i, substituteTokens(imports.get(i), "", environmentVariables));
                    imports.set(i, substituteTokens(imports.get(i), "",
                        Map.of("PROXYHOST", "localhost", "PROXYPORT", localDockerProxy.getPort())));
                }
                startDocker(server);
            } else if (uri[0].equals("external")) {
                initializeExternal(server);
            } else {
                throw new TigerTestEnvException(
                    String.format("Unsupported server type %s found in server %s", uri[0], server.getName()));
            }

            // TODO add routes needed for each server to local docker proxy
            localDockerProxy.addRoute("http://" + server.getName(),
                "http://localhost:" + server.getPorts().entrySet().stream().findFirst().get().getValue(),
                true);
            // set system properties from exports section and store the value in environmentVariables map
            server.getExports().forEach(exp -> {
                final int sep = exp.indexOf("=");
                assertThat(sep).isNotEqualTo(-1);
                final String key = exp.substring(0, sep);
                String value = exp.substring(sep + 1);
                // ports substitution are only supported for docker based instances
                if (uri[0].equals("docker") && server.getPorts() != null) {
                    for (final Entry<Integer, Integer> entry : server.getPorts().entrySet()) {
                        value = value.replace("${PORT:" + entry.getKey() + "}", String.valueOf(entry.getValue()));
                    }
                }
                value = value.replace("${NAME}", server.getName());
                log.info("  setting system property " + key + "=" + value);
                System.setProperty(key, value);
                environmentVariables.put(key, value);
            });
        } else {
            log.warn("skipping inactive server " + server.getName());
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
        // TODO SOMEHOW forward all exports from all already started servers
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

    // TODO copied from ThreadSafeDomainContextProvider
    private static String substituteTokens(String str, final String token, final Map<String, Object> valueMap) {
        final String tokenStr = "${" + (token.isBlank() ? "" : token + ".");
        int varIdx = str.indexOf(tokenStr);
        while (varIdx != -1) {
            final int endVar = str.indexOf("}", varIdx);
            final String varName = str.substring(varIdx + tokenStr.length(), endVar);
            if (valueMap.get(varName) != null) {
                str = str.substring(0, varIdx) + valueMap.get(varName) + str.substring(endVar + 1);
                varIdx = str.indexOf(tokenStr);
            } else {
                varIdx = str.indexOf(tokenStr, varIdx + 1);
            }
        }
        return str;
    }

}
