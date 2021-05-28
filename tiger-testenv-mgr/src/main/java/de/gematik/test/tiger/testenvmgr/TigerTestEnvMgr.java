/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.OSEnvironment;
import de.gematik.test.tiger.common.context.ThreadSafeDomainContextProvider;
import de.gematik.test.tiger.common.pki.KeyMgr;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class TigerTestEnvMgr implements ITigerTestEnvMgr {

    private static boolean SHUTDOWN_HOOK_ACTIVE = false;
    private final Configuration configuration;

    private final DockerMgr dockerManager;

    private final Map<String, Object> environmentVariables;

    private final TigerProxy localDockerProxy;

    private final List<String[]> routesList = new ArrayList<>();

    private final List<Process> externalProcesses = new ArrayList<>();

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
        TigerProxyConfiguration proxyConfig = configuration.getTigerProxy();
        if (proxyConfig.getProxyRoutes() == null) {
            proxyConfig.setProxyRoutes(Collections.emptyMap());
        }
        if (proxyConfig.getServerRootCaCertPem() == null) {
            proxyConfig.setServerRootCaCertPem("CertificateAuthorityCertificate.pem");
            proxyConfig.setServerRootCaKeyPem("PKCS8CertificateAuthorityPrivateKey.pem");
        }
        localDockerProxy = new TigerProxy(configuration.getTigerProxy());
    }

    @Override
    public void setUpEnvironment() {
        log.info("starting set up of test environment...");
        configuration.getServers().forEach(server -> start(server, configuration));
        log.info("finished set up test environment OK");
    }

    @Override
    public List<CfgServer> getTestEnvironment() {
        return configuration.getServers();
    }

    @Override
    public void start(final CfgServer server, Configuration configuration) {
        final String[] uri = server.getInstanceUri().split(":");

        if (server.isActive()) {

            // if proxy env are in imports replace  with localdockerproxy data
            if (uri[0].equals("docker")) {
                startDocker(server, configuration);
            } else if (uri[0].equals("compose")) {
                startDocker(server, configuration);
            } else if (uri[0].equals("external")) {
                initializeExternal(server);
            } else if (uri[0].equals("externalJar")) {
                initializeExternalJar(server);
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

    private void startDocker(final CfgServer server, Configuration configuration) {
        log.info(
            Ansi.BOLD + Ansi.GREEN + "Starting docker container for " + server.getName() + ":" + server.getInstanceUri()
                + Ansi.RESET);
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
        if (server.getInstanceUri().startsWith("docker:")) {
            dockerManager.startContainer(server, configuration, this);
        } else {
            dockerManager.startComposition(server, configuration, this);
        }
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

    @SneakyThrows
    public void initializeExternalJar(final CfgServer server) {
        log.info(Ansi.BOLD + Ansi.GREEN + "starting external jar instance " + server.getName() + "..." + Ansi.RESET);

        List<String> options = server.getOptions().stream()
            .map(o -> ThreadSafeDomainContextProvider.substituteTokens(o, "",
                Map.of("PROXYHOST", "127.0.0.1", "PROXYPORT", localDockerProxy.getPort())))
            .collect(Collectors.toList());
        options.add(0, "C:\\Program Files\\OpenJDK\\openjdk-11.0.8_10\\bin\\java.exe");
        options.add(1, "-jar");
        options.add(2, server.getInstanceUri().split(":", 2)[1]);

        log.info("executing '" + String.join(" ", options));
        Thread t = new Thread(() -> {
            try {
                externalProcesses.add(new ProcessBuilder()
                    .command(options.toArray(String[]::new))
                    .directory(new File(server.getWorkingDir()))
                    .inheritIO()
                    .start());
            } catch (IOException e) {
                throw new TigerTestEnvException("Unable to start server " + server.getName(), e);
            }
        });
        t.setName(server.getName());
        t.start();

        if (!SHUTDOWN_HOOK_ACTIVE) {
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    log.info("interrupting threads...");
                    externalProcesses.forEach(Process::destroy);
                    log.info("stopping threads...");
                    externalProcesses.forEach(Process::destroyForcibly);
                }
            });
            SHUTDOWN_HOOK_ACTIVE = true;
        }

        Thread.sleep(server.getStartupTimeoutSec()*1000);
    // TODO check availability
        log.info(Ansi.BOLD +Ansi.GREEN +"External jar server Startup OK "+server.getInstanceUri()+Ansi.RESET);
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
        srv.getPkiKeys().stream()
            .filter(key -> key.getPem().contains("BEGIN CERTIFICATE"))
            .forEach(key -> {
                log.info("Adding certificate " + key.getId());
                getLocalDockerProxy().addKey(key.getId(), KeyMgr.readCertificateFromPem(key.getPem()).getPublicKey());
            });
        srv.getPkiKeys().stream()
            .filter(key -> !key.getPem().contains("BEGIN CERTIFICATE"))
            .forEach(key -> {
                log.info("Adding key " + key.getId());
                getLocalDockerProxy().addKey(key.getId(), KeyMgr.readKeyFromPem(key.getPem()));
            });
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

    public List<String[]> getRoutes() {
        return routesList;
    }
}
