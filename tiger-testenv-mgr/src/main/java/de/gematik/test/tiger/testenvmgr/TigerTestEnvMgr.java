package de.gematik.test.tiger.testenvmgr;

import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.OSEnvironment;
import de.gematik.test.tiger.common.context.ThreadSafeDomainContextProvider;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import java.io.File;
import java.net.ServerSocket;
import java.net.URI;
import java.util.*;
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

    private final List<String[]> routesList = new ArrayList<>();

    @SneakyThrows
    public TigerTestEnvMgr() {
        // read configuration from file and templates from classpath resource
        final var cfgFile = new File(OSEnvironment.getAsString(
            "TIGER_TESTENV_CFGFILE", "tiger-testenv.yaml"));
        configuration = new Configuration();
        configuration.readConfig(cfgFile.toURI());
        final var templates = new Configuration();
        templates.readConfig(Objects.requireNonNull(getClass().getResource(
            "templates.yaml")).toURI());

        // apply templates to read in configuration
        log.info("applying server templates");
        configuration.getTemplates().addAll(templates.getTemplates());
        configuration.applyTemplates();

        environmentVariables = new HashMap<>();
        dockerManager = new DockerMgr();
        if (configuration.getTigerProxy() == null) {
            configuration.setTigerProxy(TigerProxyConfiguration.builder().build());
        }
        configuration.getTigerProxy().setProxyRoutes(Collections.emptyMap());
        configuration.getTigerProxy().setProxyLogLevel("WARN");
        configuration.getTigerProxy().setServerRootCaCertPem("CertificateAuthorityCertificate.pem");
        configuration.getTigerProxy().setServerRootCaKeyPem("PKCS8CertificateAuthorityPrivateKey.pem");

        localDockerProxy = new TigerProxy(configuration.getTigerProxy());
    }

    @Override
    public void setUpEnvironment() {
        log.info("starting set up of test environment...");
        configuration.getServers().forEach(this::start);
        log.info("finished set up test environment OK");
    }

    @Override
    public List<CfgServer> getTestEnvironment() {
        return configuration.getServers();
    }

    @Override
    public void start(final CfgServer server) {
        final String[] uri = server.getInstanceUri().split(":");

        if (server.isActive()) {

            // if proxy env are in imports replace  with localdockerproxy data
            if (uri[0].equals("docker")) {
                startDocker(server);
            } else if (uri[0].equals("external")) {
                initializeExternal(server);
            } else {
                throw new TigerTestEnvException(
                    String.format("Unsupported server type %s found in server %s", uri[0], server.getName()));
            }

            // set system properties from exports section and store the value in environmentVariables map
            server.getExports().forEach(exp -> {
                String[] kvp = exp.split("=", 2);
                // ports substitution are only supported for docker based instances
                if (uri[0].equals("docker") && server.getPorts() != null) {
                    server.getPorts().forEach((localPort, externPort) ->
                        kvp[1] = kvp[1].replace("${PORT:" + localPort + "}", String.valueOf(externPort))
                    );
                }
                kvp[1] = kvp[1].replace("${NAME}", server.getName());

                log.info("  setting system property " + kvp[0] + "=" + kvp[1]);
                System.setProperty(kvp[0], kvp[1]);
                environmentVariables.put(kvp[0], kvp[1]);
            });
        } else {
            log.warn("skipping inactive server " + server.getName());
        }
    }

    private void startDocker(final CfgServer server) {
        log.info(Ansi.BOLD + Ansi.GREEN + "Starting docker container for " + server.getInstanceUri() + Ansi.RESET);
        final List<String> imports = server.getImports();
        for (var i = 0; i < imports.size(); i++) {
            imports.set(i, ThreadSafeDomainContextProvider.substituteTokens(imports.get(i), "", environmentVariables));
            imports.set(i, ThreadSafeDomainContextProvider.substituteTokens(imports.get(i), "",
                Map.of("PROXYHOST", "host.docker.internal", "PROXYPORT", localDockerProxy.getPort())));
        }
        if (server.getUrlMappings() != null) {
            server.getUrlMappings().forEach(mapping -> {
                String[] kvp = mapping.split(" --> ", 2);
                localDockerProxy.addRoute(kvp[0], kvp[1]);
            });
        }
        dockerManager.startContainer(server, this);
        loadPKIForServer(server);
        // add routes needed for each server to local docker proxy
        // ATTENTION only one route per server!
        if (server.getPorts() != null && !server.getPorts().isEmpty()) {
            routesList.add(new String[]{"http://" + server.getName(),
                "http://localhost:" + server.getPorts().values().iterator().next()});
            localDockerProxy.addRoute("http://" + server.getName(),
                "http://localhost:" + server.getPorts().values().iterator().next());
        }
        log.info(Ansi.BOLD + Ansi.GREEN + "Docker container Startup OK " + server.getInstanceUri() + Ansi.RESET);
    }

    @SneakyThrows
    public void initializeExternal(final CfgServer server) {
        log.info(Ansi.BOLD + Ansi.GREEN + "starting external instance " + server.getName() + "..." + Ansi.RESET);
        final var uri = new URI(server.getInstanceUri().substring("external:".length()));

        localDockerProxy.addRoute("http://" + server.getName(), uri.getScheme() + "://" + uri.getHost());

        loadPKIForServer(server);
        log.info("  Checking external instance  " + server.getName() + " is available ...");
        // TODO check availability, configure http client to use 5 s timeout max, run in loop for timeout config value
        log.info(Ansi.BOLD + Ansi.GREEN + "External server Startup OK " + server.getInstanceUri() + Ansi.RESET);
    }

    @Override
    public void shutDown(final CfgServer server) {
        final String[] uri = server.getInstanceUri().split(":");
        if (uri[0].equals("external")) {
            shutDownExternal(server);
        } else if (uri[0].equals("docker")) {
            shutDownDocker(server);
        } else {
            throw new TigerTestEnvException("Unsupported server uri type " + server.getInstanceUri());
        }
    }

    private void loadPKIForServer(final CfgServer srv) {
        log.info("  loading PKI resources for instance " + srv.getName() + "...");
        // TODO initialize pki cert and key pool either from local folder
        //  if env TIGER_TESTENV_PKIFOLDER is set
        // or from classpath in resources/pki
    }

    private void shutDownDocker(final CfgServer server) {
        dockerManager.stopContainer(server);
        removeRoute(server);
    }

    private void shutDownExternal(final CfgServer server) {
        removeRoute(server);
    }

    private void removeRoute(CfgServer server) {
        String serverUrl = "http://" + server.getName();
        localDockerProxy.removeRoute(serverUrl);
        routesList.remove(routesList.stream()
            .filter(r -> r[0].equals("http://" + server.getName()))
            .findAny()
            .orElseThrow()
        );
    }

    @SuppressWarnings("unused")
    @SneakyThrows
    private Integer getFreePort() {
        try (final var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    public List<String[]> getRoutes() {
        return routesList;
    }
}
