/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.OsEnvironment;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.common.config.TigerConfigurationHelper;
import de.gematik.test.tiger.common.pki.KeyMgr;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.data.TigerRoute;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
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

    private static final String HTTP = "http://";
    private static final String HTTPS = "https://";
    private static boolean SHUTDOWN_HOOK_ACTIVE = false;
    private final Configuration configuration;

    private final DockerMgr dockerManager;

    private Map<String, Object> environmentVariables;

    private final TigerProxy localDockerProxy;

    private final List<TigerRoute> routesList = new ArrayList<>();

    private final Map<String, Process> externalProcesses = new HashMap<>();

    public static void main(String[] args) throws InterruptedException {
        try {
            TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
            envMgr.setUpEnvironment();
        } catch (Exception e) {
            log.error("Error while starting up stand alone tiger testenv mgr!", e);
            System.exit(1);
        }
        log.info("Tiger standalone test environment UP!");
        while(true) {
            Thread.sleep(1000);
        }
    }

    @SneakyThrows
    public TigerTestEnvMgr() {
        // read configuration from file and templates from classpath resource
        var cfgFile = new File(OsEnvironment.getAsString(
            "TIGER_TESTENV_CFGFILE", "tiger-testenv-" + getComputerName() + ".yaml"));
        if (!cfgFile.exists()) {
            log.warn("Unable to read configuration from " + cfgFile.getAbsolutePath());
            cfgFile = new File("tiger-testenv.yaml");
        }
        log.info("Reading configuration from " + cfgFile.getAbsolutePath() + "...");
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
            proxyConfig.setProxyRoutes(List.of());
        }
        if (proxyConfig.getServerRootCaCertPem() == null) {
            proxyConfig.setServerRootCaCertPem("CertificateAuthorityCertificate.pem");
            proxyConfig.setServerRootCaKeyPem("PKCS8CertificateAuthorityPrivateKey.pem");
        }
        log.info("Starting local docker tiger proxy...");
        localDockerProxy = new TigerProxy(configuration.getTigerProxy());
        environmentVariables = new HashMap<>(
            Map.of("PROXYHOST", "host.docker.internal",
                "PROXYPORT", localDockerProxy.getPort()));
        log.info("Tiger Testenv mgr created OK");
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

    private String getComputerName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return InetAddress.getLoopbackAddress().getHostName();
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
                localDockerProxy.addRoute(TigerRoute.builder()
                    .from(kvp[0])
                    .to(kvp[1])
                    .build());
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
            routesList.add(TigerRoute.builder()
                .from("http://" + server.getName())
                .to("http://localhost:" + server.getPorts().values().iterator().next())
                .build());
            localDockerProxy.addRoute(TigerRoute.builder()
                .from("http://" + server.getName())
                .to("http://localhost:" + server.getPorts().values().iterator().next())
                .build());
        }
        log.info(Ansi.BOLD + Ansi.GREEN + "Docker container Startup OK " + server.getSource().get(0) + Ansi.RESET);
    }

    @SneakyThrows
    public void initializeExternal(final CfgServer server) {
        log.info(Ansi.BOLD + Ansi.GREEN + "starting external instance " + server.getName() + "..." + Ansi.RESET);
        final var uri = new URI(server.getSource().get(0));

        localDockerProxy.addRoute(TigerRoute.builder()
            .from("http://" + server.getName())
            .to(uri.getScheme() + "://" + uri.getHost())
            .build());

        loadPKIForProxy(server);
        log.info("  Waiting 50% of start up time for external instance  " + server.getName() + " to come up ...");
        long startms = System.currentTimeMillis();
        try {
            Thread.sleep(server.getStartupTimeoutSec() * 500L);
        } catch (InterruptedException ie) {
            log.warn("Interruption while waiting for external server to respond!", ie);
            Thread.currentThread().interrupt();
        }
        log.info("  Checking external instance  " + server.getName() + " is available ...");
        try {
            InsecureRestorableTrustAllManager.saveContext();
            InsecureRestorableTrustAllManager.allowAllSSL();
            while (System.currentTimeMillis() - startms < server.getStartupTimeoutSec() * 1000L) {
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
            InsecureRestorableTrustAllManager.restoreContext();
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
                externalProcesses.put(server.getName(), new ProcessBuilder()
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
                externalProcesses.values().forEach(Process::destroy);
                log.info("stopping threads...");
                externalProcesses.values().forEach(Process::destroyForcibly);
            }));
            SHUTDOWN_HOOK_ACTIVE = true;
        }

        if (server.getHealthcheck() == null || server.getHealthcheck().equals("NONE")) {
            log.info("Waiting " + server.getStartupTimeoutSec() + "s to get external jar online...");
            Thread.sleep(server.getStartupTimeoutSec() * 1000);
            return;
        }
        long startms = System.currentTimeMillis();
        try {
            InsecureRestorableTrustAllManager.saveContext();
            InsecureRestorableTrustAllManager.allowAllSSL();
            while (System.currentTimeMillis() - startms < server.getStartupTimeoutSec() * 1000) {
                if (exception.get() != null) {
                    throw new TigerTestEnvException("Unable to start external jar!", exception.get());
                }
                URL url = new URL(server.getHealthcheck());
                URLConnection con = url.openConnection();
                con.setConnectTimeout(1000);
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
                if (!externalProcesses.get(server.getName()).isAlive()) {
                    throw new TigerTestEnvException(
                        "Process aborted with exit code " + externalProcesses.get(server.getName())
                            .exitValue());
                }
            }
            throw new TigerTestEnvException("Timeout while waiting for external jar to start!");
        } finally {
            InsecureRestorableTrustAllManager.restoreContext();
        }
    }

    private void downloadJar(CfgServer server, String jarUrl, File jarFile) throws InterruptedException {
        if (jarUrl.startsWith("local:")) {
            throw new TigerTestEnvException("Local jar " + jarFile.getAbsolutePath() + " not found!");
        }
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
        } else if (type.equalsIgnoreCase("externalJar")) {
            shutDownJar(server);
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
        log.info("Stopping docker container " + server.getName() + "...");
        dockerManager.stopContainer(server);
        removeRoute(server);
    }

    private void shutDownExternal(final CfgServer server) {
        removeRoute(server);
    }

    private void shutDownJar(final CfgServer server) {
        Process proc = externalProcesses.get(server.getName());
        log.info("interrupting thread for " + server.getName() + "...");
        proc.destroy();
        log.info("stopping thread for " + server.getName() + "...");
        proc.destroyForcibly();
        removeRoute(server);
    }

    private void removeRoute(CfgServer server) {
        log.info("Removing routes for " + server.getName() + "...");
        Predicate<TigerRoute> isServerRoute = route -> route.getFrom().equals(HTTP + server.getName())
            || route.getFrom().equals(HTTPS + server.getName());
        routesList.stream()
            .filter(isServerRoute)
            .forEach(r -> localDockerProxy.removeRoute(r.getFrom()));
        routesList.removeIf(isServerRoute);
    }


    public List<TigerRoute> getRoutes() {
        return routesList;
    }
}
