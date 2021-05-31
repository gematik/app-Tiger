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
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@Slf4j
@Getter
public class TigerTestEnvMgr implements ITigerTestEnvMgr {

    private static boolean SHUTDOWN_HOOK_ACTIVE = false;
    private static String HTTP = "http://";
    private static String HTTPS = "https://";

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
        final String type = server.getType();

        if (server.isActive()) {

            // if proxy env are in imports replace  with localdockerproxy data
            if (type.equalsIgnoreCase("docker")) {
                startDocker(server, configuration);
            } else if (type.equalsIgnoreCase("compose")) {
                startDocker(server, configuration);
            } else if (type.equalsIgnoreCase("externalUrl")) {
                initializeExternal(server);
            } else if (type.equalsIgnoreCase("externalJar")) {
                initializeExternalJar(server);
            } else {
                throw new TigerTestEnvException(
                    String.format("Unsupported server type %s found in server %s", type, server.getName()));
            }

            // set system properties from exports section and store the value in environmentVariables map
            server.getExports().forEach(exp -> {
                String[] kvp = exp.split("=", 2);
                // ports substitution are only supported for docker based instances
                if (type.equalsIgnoreCase("docker") && server.getPorts() != null) {
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
            Ansi.BOLD + Ansi.GREEN + "Starting docker container for " + server.getName() + ":" + server.getSource().get(0)
                + Ansi.RESET);
        final List<String> imports = server.getEnvironment();
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
        if (server.getType().equalsIgnoreCase("docker")) {
            dockerManager.startContainer(server, configuration, this);
        } else {
            dockerManager.startComposition(server, configuration, this);
        }
        loadPKIForProxy(server);
        // add routes needed for each server to local docker proxy
        // ATTENTION only one route per server!
        if (server.getPorts() != null && !server.getPorts().isEmpty()) {
            routesList.add(new String[]{"http://" + server.getName(),
                "http://localhost:" + server.getPorts().values().iterator().next()});
            localDockerProxy.addRoute("http://" + server.getName(),
                "http://localhost:" + server.getPorts().values().iterator().next());
        }
        log.info(Ansi.BOLD + Ansi.GREEN + "Docker container Startup OK " + server.getSource().get(0) + Ansi.RESET);
    }

    @SneakyThrows
    public void initializeExternal(final CfgServer server) {
        log.info(Ansi.BOLD + Ansi.GREEN + "starting external instance " + server.getName() + "..." + Ansi.RESET);
        final var uri = new URI(server.getSource().get(0));

        localDockerProxy.addRoute("http://" + server.getName(), uri.getScheme() + "://" + uri.getHost());

        loadPKIForProxy(server);
        log.info("  Checking external instance  " + server.getName() + " is available ...");
        // TODO check availability, configure http client to use 5 s timeout max, run in loop for timeout config value
        log.info(Ansi.BOLD + Ansi.GREEN + "External server Startup OK " + server.getSource().get(0) + Ansi.RESET);
    }

    @SneakyThrows
    public void initializeExternalJar(final CfgServer server) {
        var jarUrl = server.getSource().get(0);
        var jarName = jarUrl.substring(jarUrl.lastIndexOf("/")+1);

        File jarFile = Paths.get(server.getWorkingDir(), jarName).toFile();
        if (!jarFile.exists()) {
            log.info("downloading jar for external server from " + jarUrl + "...");
            File workDir = new File(server.getWorkingDir());
            if (!workDir.exists() && !workDir.mkdirs()) {
                throw new TigerTestEnvException("Unable to create working directory " + workDir.getAbsolutePath());
            }
            FileUtils.copyURLToFile(new URL(jarUrl), jarFile);
            // TODO add thread informing about download status
        }

        log.info(Ansi.BOLD + Ansi.GREEN + "starting external jar instance " + server.getName() + "..." + Ansi.RESET);

        List<String> options = server.getOptions().stream()
            .map(o -> ThreadSafeDomainContextProvider.substituteTokens(o, "",
                Map.of("PROXYHOST", "127.0.0.1", "PROXYPORT", localDockerProxy.getPort())))
            .collect(Collectors.toList());
        // TODO check for java process being in PATH
        // (actually iterate over all entries and try to find java or java.exe depending on OS)
        options.add(0, "C:\\Program Files\\OpenJDK\\openjdk-11.0.8_10\\bin\\java.exe");
        options.add("-jar");
        options.add(jarName);
        options.addAll(server.getArguments());
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
        log.info(Ansi.BOLD +Ansi.GREEN +"External jar server Startup OK "+server.getSource()+Ansi.RESET);
}

    @Override
    public void shutDown(final CfgServer server) {
        final String type = server.getType();
        if (type.equalsIgnoreCase("externalUrl")) {
            shutDownExternal(server);
        } else if (type.equalsIgnoreCase("docker")) {
            shutDownDocker(server);
        } else {
            // TODO externalJar, docker compose
            throw new TigerTestEnvException("Unsupported server uri type " + type);
        }
    }

    private void loadPKIForProxy(final CfgServer srv) {
        log.info("  loading PKI resources for instance " + srv.getName() + "...");
        srv.getPkiKeys().stream()
            .filter(key -> key.getType().equals("cert"))
            .forEach(key -> {
                log.info("Adding certificate " + key.getId());
                getLocalDockerProxy().addKey(
                    key.getId(),
                    KeyMgr.readCertificateFromPem("-----BEGIN CERTIFICATE-----\n"
                        + key.getPem().replace(" ", "\n")
                        + "\n-----END CERTIFICATE-----").getPublicKey());
            });
        srv.getPkiKeys().stream()
            .filter(key -> key.getType().equals("key"))
            .forEach(key -> {
                log.info("Adding key " + key.getId());
                getLocalDockerProxy().addKey(
                    key.getId(),
                    KeyMgr.readKeyFromPem("-----BEGIN PRIVATE KEY-----\n"
                        + key.getPem().replace(" ", "\n")
                        + "\n-----END PRIVATE KEY-----"));
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
        String serverUrl = server.getName();
        localDockerProxy.removeRoute(HTTP + serverUrl);
        localDockerProxy.removeRoute(HTTPS + serverUrl);
        routesList.remove(routesList.stream()
            .filter(r -> r[0].equals(HTTP + server.getName()) || r[0].equals(HTTPS + server.getName()))
            .findAny()
            .orElseThrow()
        );
    }

    public List<String[]> getRoutes() {
        return routesList;
    }
}
