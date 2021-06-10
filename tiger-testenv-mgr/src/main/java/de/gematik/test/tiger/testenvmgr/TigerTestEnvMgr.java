/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.OSEnvironment;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.common.config.TigerConfigurationHelper;
import de.gematik.test.tiger.common.pki.KeyMgr;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.json.JSONObject;

@Slf4j
@Getter
public class TigerTestEnvMgr implements ITigerTestEnvMgr {

    private static boolean SHUTDOWN_HOOK_ACTIVE = false;
    private static final String HTTP = "http://";
    private static final String HTTPS = "https://";

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
        JSONObject jsonCfg = TigerConfigurationHelper.yamlToJson(cfgFile.getAbsolutePath());

        JSONObject jsonTemplate = TigerConfigurationHelper.yamlStringToJson(
            IOUtils.toString(Objects.requireNonNull(getClass().getResource(
                "templates.yaml")).toURI(), StandardCharsets.UTF_8));
        TigerConfigurationHelper.applyTemplate(
            jsonCfg.getJSONArray("servers"), "template",
            jsonTemplate.getJSONArray("templates"), "name");

        TigerConfigurationHelper.overwriteWithSysPropsAndEnvVars("TIGER_TESTENV", "tiger.testenv", jsonCfg);

        configuration = new TigerConfigurationHelper<Configuration>()
            .jsonStringToConfig(jsonCfg.toString(), Configuration.class);

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
        environmentVariables = new HashMap<>(
            Map.of("PROXYHOST", "host.docker.internal",
                "PROXYPORT", localDockerProxy.getPort()));
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
            Ansi.BOLD + Ansi.GREEN + "Starting docker container for " + server.getName() + ":" + server.getSource()
                .get(0)
                + Ansi.RESET);
        final List<String> imports = server.getEnvironment();
        for (var i = 0; i < imports.size(); i++) {
            imports.set(i, TokenSubstituteHelper.substitute(imports.get(i), "", environmentVariables));
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
        try {
            HttpsTrustManager.saveContext();
            HttpsTrustManager.allowAllSSL();
            long startms = System.currentTimeMillis();
            while (System.currentTimeMillis() - startms < server.getStartupTimeoutSec() * 1000) {
                var url = new URL(server.getHealthcheck());
                URLConnection con = url.openConnection();
                con.setConnectTimeout(1000);
                try {
                    con.connect();
                    log.info("External node " + server.getName() + " is online");
                    log.info(Ansi.BOLD + Ansi.GREEN + "External server Startup OK " + server.getSource().get(0)
                        + Ansi.RESET);
                    return;
                } catch (Exception e) {
                    log.info("Connection failed - " + e.getMessage());
                }
                Thread.sleep(1000);
            }
            throw new TigerTestEnvException("Timeout waiting for external server to respond!");
        } finally {
            HttpsTrustManager.restoreContext();
        }
    }

    @SneakyThrows
    public void initializeExternalJar(final CfgServer server) {
        var jarUrl = server.getSource().get(0);
        var jarName = jarUrl.substring(jarUrl.lastIndexOf("/") + 1);
        var jarFile = Paths.get(server.getWorkingDir(), jarName).toFile();

        if (!jarFile.exists()) {
            downloadJar(server, jarUrl, jarFile);
        }

        log.info(Ansi.BOLD + Ansi.GREEN + "starting external jar instance " + server.getName() + " in folder " + server
            .getWorkingDir() + "..." + Ansi.RESET);

        List<String> options = server.getOptions().stream()
            .map(o -> TokenSubstituteHelper.substitute(o, "",
                Map.of("PROXYHOST", "127.0.0.1", "PROXYPORT", localDockerProxy.getPort())))
            .collect(Collectors.toList());
        String[] paths = System.getenv("PATH").split(SystemUtils.IS_OS_WINDOWS ? ";" : ":");
        String javaProg = "java" + (SystemUtils.IS_OS_WINDOWS ? ".exe" : "");
        String javaExe = Arrays.stream(paths)
            .map(path -> Path.of(path, javaProg).toFile())
            .filter(file -> file.exists() && file.canExecute())
            .map(File::getAbsolutePath)
            .findAny()
            .orElseThrow(() -> new TigerTestEnvException("Unable to find executable java program in PATH"));
        options.add(0, javaExe);
        options.add("-jar");
        options.add(jarName);
        options.addAll(server.getArguments());
        log.info("executing '" + String.join(" ", options));
        final AtomicReference<Throwable> exception = new AtomicReference<>();
        var thread = new Thread(() -> {
            try {
                externalProcesses.add(new ProcessBuilder()
                    .command(options.toArray(String[]::new))
                    .directory(new File(server.getWorkingDir()))
                    .inheritIO()
                    .start());
            } catch (Throwable t) {
                exception.set(t);
            }
        });
        thread.setName(server.getName());
        thread.start();

        if (!SHUTDOWN_HOOK_ACTIVE) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("interrupting threads...");
                externalProcesses.forEach(Process::destroy);
                log.info("stopping threads...");
                externalProcesses.forEach(Process::destroyForcibly);
            }));
            SHUTDOWN_HOOK_ACTIVE = true;
        }

        try {
            HttpsTrustManager.saveContext();
            HttpsTrustManager.allowAllSSL();
            var started = false;
            long startms = System.currentTimeMillis();
            while (System.currentTimeMillis() - startms < server.getStartupTimeoutSec() * 1000) {
                if (exception.get() != null) {
                    throw new TigerTestEnvException("Unable to start external jar!", exception.get());
                }
                URL url = new URL(server.getHealthcheck());
                URLConnection con = url.openConnection();
                con.setConnectTimeout(1);
                try {
                    con.connect();
                    log.info("External jar node " + server.getName() + " is online");
                    log.info(
                        Ansi.BOLD + Ansi.GREEN + "External jar server Startup OK " + server.getSource() + Ansi.RESET);
                    return;
                } catch (Exception e) {
                    log.info("Failed to connect - " + e.getMessage());
                }
                Thread.sleep(1000);
                if (!externalProcesses.get(externalProcesses.size() - 1).isAlive()) {
                    throw new TigerTestEnvException(
                        "Process aborted with exit code " + externalProcesses.get(externalProcesses.size() - 1)
                            .exitValue());
                }
            }
            throw new TigerTestEnvException("Timeout while waiting for external jar to start!");
        } finally {
            HttpsTrustManager.restoreContext();
        }
    }

    private void downloadJar(CfgServer server, String jarUrl, File jarFile) throws InterruptedException {
        log.info("downloading jar for external server from " + jarUrl + "...");
        var workDir = new File(server.getWorkingDir());
        if (!workDir.exists() && !workDir.mkdirs()) {
            throw new TigerTestEnvException("Unable to create working directory " + workDir.getAbsolutePath());
        }
        long startms = System.currentTimeMillis();
        var finished = new AtomicBoolean(false);
        var exception = new AtomicReference<Exception>();
        var t = new Thread(() -> {
            try {
                FileUtils.copyURLToFile(new URL(jarUrl), jarFile);
                finished.set(true);
            } catch (IOException ioe) {
                exception.set(ioe);
            }
        });
        t.start();
        var progressCtr = 0;
        while (!finished.get()) {
            if (System.currentTimeMillis() - startms > 600 * 1000) {
                t.interrupt();
                t.stop();
                throw new TigerTestEnvException("Download of " + jarUrl + " took longer then 10 minutes!");
            }
            if (exception.get() != null) {
                throw new TigerTestEnvException("Failure while downloading jar " + jarUrl + "!", exception.get());
            }
            Thread.sleep(500);
            progressCtr++;
            if (progressCtr == 6) {
                log.info("downloaded " + jarFile.length() / 1000 + " kb");
                progressCtr = 0;
            }
        }
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
