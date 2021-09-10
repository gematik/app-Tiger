/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.OsEnvironment;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.common.banner.Banner;
import de.gematik.test.tiger.common.config.TigerConfigurationHelper;
import de.gematik.test.tiger.common.pki.KeyMgr;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.data.TigerRoute;
import de.gematik.test.tiger.testenvmgr.config.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
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
    private final Map<String, Object> environmentVariables;
    private final TigerProxy localTigerProxy;
    private final List<TigerRoute> routesList = new ArrayList<>();
    private final Map<String, Process> externalProcesses = new HashMap<>();


    public static void main(String[] args) {

        TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        try {
            envMgr.setUpEnvironment();
        } catch (Exception e) {
            log.error("Error while starting up stand alone tiger testenv mgr! ABORTING...", e);
            System.exit(1);
        }
        log.info("\n" + Banner.toBannerStr("Tiger standalone test environment UP!", Ansi.BOLD + Ansi.GREEN));
        waitForQuit(null);
        log.info("interrupting " + envMgr.externalProcesses.size() + " threads...");
        envMgr.externalProcesses.values().forEach(Process::destroy);
        log.info("stopping threads...");
        envMgr.externalProcesses.values().forEach(Process::destroyForcibly);
        envMgr.externalProcesses.clear();
        System.exit(0);
    }

    public static void waitForQuit(String message, Object... args) {
        Console c = System.console();
        if (c != null) {
            // printf-like arguments
            if (message != null) {
                c.format(message, args);
            }
            c.format("\n\n\nPress 'quit' and ENTER to stop TIGER standalone test environment.\n\n\n\n\n");
            String cmd = "";
            while (!cmd.equals("quit")) {
                cmd = c.readLine();
            }
            log.info("Stopping TIGER standalone test environment...");
        } else {
            log.warn("No Console interface found, trying System in stream...");
            log.info("\n\n\nPress 'quit' and ENTER to stop TIGER standalone test environment.\n\n\n\n\n");
            try {
                BufferedReader rdr = new BufferedReader(
                    new InputStreamReader(System.in));
                String cmd = "";
                while (!cmd.equals("quit")) {
                    cmd = rdr.readLine();
                }
            } catch (IOException e) {
                log.warn(
                    "Unable to open input stream from console! You will have to use Ctrl+C and clean up the processes manually!");
                while (true) { // NOSONAR
                    try {
                        Thread.sleep(1000L); // NOSONAR
                    } catch (InterruptedException ie) {
                        return;
                    }
                }
            }
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
        if (proxyConfig.getServerRootCa() == null) {
            proxyConfig.setServerRootCa(new TigerPkiIdentity(
                "CertificateAuthorityCertificate.pem;PKCS8CertificateAuthorityPrivateKey.pem;PKCS8"));
        }
        log.info("Starting local docker tiger proxy...");
        localTigerProxy = new TigerProxy(configuration.getTigerProxy());
        if (configuration.isLocalProxyActive()) {
            environmentVariables = new HashMap<>(
                Map.of("PROXYHOST", "host.docker.internal",
                    "PROXYPORT", localTigerProxy.getPort()));
        } else {
            environmentVariables = new HashMap<>();
        }
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
        final ServerType type = server.getType();

        if (!server.isActive()) {
            log.warn("skipping inactive server " + server.getName());
            return;
        }

        if (type == null) {
            throw new TigerTestEnvException("Type is not specified for server node '" + server.getName() + "'!");
        }
        // if proxy env are in imports replace  with localdockerproxy data
        switch (type) {
            case DOCKER:
            case DOCKER_COMPOSE:
                startDocker(server, configuration);
                break;
            case EXTERNALURL:
                initializeExternalUrl(server);
                break;
            case REVERSEPROXY:
                initializeReverseProxy(server, configuration);
                break;
            case EXTERNALJAR:
                initializeExternalJar(server);
                break;
            default:
                throw new TigerTestEnvException(
                    String.format("Unsupported server type %s found in server %s", type, server.getName()));
        }
        server.setStarted(true);

        // set system properties from exports section and store the value in environmentVariables map
        server.getExports().forEach(exp -> {
            String[] kvp = exp.split("=", 2);
            // ports substitution are only supported for docker based instances
            if (type == ServerType.DOCKER && server.getPorts() != null) {
                server.getPorts().forEach((localPort, externPort) ->
                    kvp[1] = kvp[1].replace("${PORT:" + localPort + "}", String.valueOf(externPort))
                );
            }
            kvp[1] = kvp[1].replace("${NAME}", server.getName());

            log.info("  setting system property " + kvp[0] + "=" + kvp[1]);
            System.setProperty(kvp[0], kvp[1]);
            environmentVariables.put(kvp[0], kvp[1]);
        });


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
                localTigerProxy.addRoute(TigerRoute.builder()
                    .from(kvp[0])
                    .to(kvp[1])
                    .build());
            });
        }
        if (server.getType() == ServerType.DOCKER) {
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
            localTigerProxy.addRoute(TigerRoute.builder()
                .from("http://" + server.getName())
                .to("http://localhost:" + server.getPorts().values().iterator().next())
                .build());
        }
        log.info(Ansi.BOLD + Ansi.GREEN + "Docker container Startup OK " + server.getSource().get(0) + Ansi.RESET);
    }

    @SneakyThrows
    private void initializeReverseProxy(CfgServer server, Configuration configuration) {
        CfgReverseProxy reverseProxyCfg = server.getReverseProxyCfg();

        CfgStandaloneProxy standaloneCfg = new CfgStandaloneProxy();
        standaloneCfg.setServer(new CfgStandaloneServer());
        standaloneCfg.getServer().setPort(reverseProxyCfg.getServerPort());

        standaloneCfg.setTigerProxy(reverseProxyCfg.getProxyCfg());
        if (reverseProxyCfg.getProxyCfg().getProxyRoutes() == null) {
            reverseProxyCfg.getProxyCfg().setProxyRoutes(new ArrayList<>());
        }

        if (reverseProxyCfg.getProxiedServer() != null) {
            getDestinationUrlFromProxiedServer(server, configuration, reverseProxyCfg);
        }
        final String downloadUrl;
        final String jarFile = "tiger-standalone-proxy-" + server.getVersion() + ".jar";
        if (reverseProxyCfg.getRepo().equals("nexus")) {
            downloadUrl =
                "https://build.top.local/nexus/service/local/repositories/releases/content/de/gematik/test/tiger-standalone-proxy/"
                    + server.getVersion() + "/" + jarFile;
        } else {
            downloadUrl = "https://repo1.maven.org/maven2/de/gematik/test/tiger-standalone-proxy/"
                + server.getVersion() + "/" + jarFile;
        }
        final File folder;
        if (server.getWorkingDir() == null) {
            folder = Path.of(System.getProperty("java.io.tmpdir"), "tiger_downloads").toFile();
            if (!folder.exists()) {
                if (!folder.mkdirs()) {
                    throw new TigerTestEnvException("Unable to create temp folder " + folder.getAbsolutePath());
                }
            }
            server.setWorkingDir(folder.getAbsolutePath());
        }else {
            folder = new File(server.getWorkingDir());
        }
        server.setSource(List.of(downloadUrl));
        server.setHealthcheck("http://127.0.0.1:" + reverseProxyCfg.getServerPort());
        if (server.getStartupTimeoutSec() == null) {
            server.setStartupTimeoutSec(20);
        }
        if (server.getArguments() == null) {
            server.setArguments(new ArrayList<>());
        }
        server.getArguments().add("--spring.profiles.active=" + server.getName());

        ObjectMapper om = new ObjectMapper(new YAMLFactory());
        om.writeValue(Path.of(folder.getAbsolutePath(), "application-" + server.getName() + ".yaml").toFile(), standaloneCfg);

        initializeExternalJar(server);
    }

    private void getDestinationUrlFromProxiedServer(CfgServer server, Configuration configuration, CfgReverseProxy cfg) {
        final String destUrl;
        CfgServer proxiedServer = configuration.getServers().stream()
            .filter(srv -> srv.getName().equals(cfg.getProxiedServer()))
            .findAny().orElseThrow(
                () -> new TigerTestEnvException(
                    "Proxied server '" + cfg.getProxiedServer() + "' not found in list!"));
        switch (proxiedServer.getType()) {
            case DOCKER:
                if (proxiedServer.getHealthcheck() == null) {
                    if (!proxiedServer.isStarted()) {
                        throw new TigerTestEnvException("If reverse proxy is to be used with docker container '"
                            + proxiedServer.getName()
                            + "' make sure to start it first or have a valid healthcheck setting!");
                    } else {
                        destUrl =
                            cfg.getProxyProtocol() + "://127.0.0.1:" + server.getPorts().values().iterator().next();
                    }
                } else {
                    destUrl = proxiedServer.getHealthcheck();
                }
                break;
            case EXTERNALJAR:
                assertThat(proxiedServer.getHealthcheck())
                    .withFailMessage(
                        "To be proxied server '" + proxiedServer.getName() + "' has no valid healthcheck Url")
                    .isNotBlank();
                destUrl = proxiedServer.getHealthcheck();
                break;
            case EXTERNALURL:
                assertThat(proxiedServer.getSource())
                    .withFailMessage(
                        "To be proxied server '" + proxiedServer.getName() + "' has no sources configured")
                    .isNotEmpty();
                assertThat(proxiedServer.getSource().get(0))
                    .withFailMessage(
                        "To be proxied server '" + proxiedServer.getName() + "' has empty source[0] configured")
                    .isNotBlank();
                destUrl = proxiedServer.getSource().get(0);
                break;
            case DOCKER_COMPOSE:
            default:
                throw new TigerTestEnvException(
                    "Sophisticated reverse proxy for '" + proxiedServer.getType() + "' is not supported!");
        }
        TigerRoute tigerRoute = new TigerRoute();
        tigerRoute.setFrom("/");
        tigerRoute.setTo(destUrl);
        cfg.getProxyCfg().getProxyRoutes().add(tigerRoute);
    }


    @SneakyThrows
    public void initializeExternalUrl(final CfgServer server) {
        log.info(Ansi.BOLD + Ansi.GREEN + "starting external URL instance " + server.getName() + "..." + Ansi.RESET);
        final var url = new URL(server.getSource().get(0));
        int port = url.getPort();
        if (port == -1) {
            port = url.getDefaultPort();
        }

        localTigerProxy.addRoute(TigerRoute.builder()
            .from("http://" + server.getName())
            .to(url.toURI().getScheme() + "://" + url.getHost() + ":" + port)
            .build());

        loadPKIForProxy(server);
        log.info("  Waiting 50% of start up time for external URL instance  " + server.getName() + " to come up ...");

        if (waitForService(server, server.getStartupTimeoutSec() * 500L, true)) {
            return;
        }
        waitForService(server, server.getStartupTimeoutSec() * 500L, false);
    }

    @SneakyThrows
    public void initializeExternalJar(final CfgServer server) {
        var jarUrl = server.getSource().get(0);
        var jarName = jarUrl.substring(jarUrl.lastIndexOf("/") + 1);
        var jarFile = Paths.get(server.getWorkingDir(), jarName).toFile();

        if (!jarFile.exists()) {
            if (jarUrl.startsWith("local:")) {
                throw new TigerTestEnvException("Local jar " + jarFile.getAbsolutePath() + " not found!");
            }
            downloadJar(server, jarUrl, jarFile);
        }

        log.info(Ansi.BOLD + Ansi.GREEN + "starting external jar instance " + server.getName() + " in folder " + server
            .getWorkingDir() + "..." + Ansi.RESET);

        List<String> options = server.getOptions().stream()
            .map(o -> {
                if (configuration.isLocalProxyActive()) {
                    return TokenSubstituteHelper.substitute(o, "",
                        Map.of("PROXYHOST", "127.0.0.1", "PROXYPORT", localTigerProxy.getPort()));
                } else {
                    return o;
                }
            })
            .map(o -> TokenSubstituteHelper.substitute(o, "", environmentVariables))
            .collect(Collectors.toList());
        String javaExe = findJavaExecutable();
        options.add(0, javaExe);
        options.add("-jar");
        options.add(jarName);
        options.addAll(server.getArguments());
        log.info("executing '" + String.join(" ", options));
        final AtomicReference<Throwable> exception = new AtomicReference<>();
        var thread = new Thread(() -> {
            try {
                Process p = new ProcessBuilder()
                    .command(options.toArray(String[]::new))
                    .directory(new File(server.getWorkingDir()))
                    .inheritIO()
                    .start();
                Thread.sleep(2000);
                if (p.isAlive()) {
                    externalProcesses.put(server.getName(), p);
                    log.info("Started " + server.getName());
                } else {
                    throw new TigerTestEnvException("External Jar startup failed");
                }
            } catch (Throwable t) {
                exception.set(t);
            }
        });
        thread.setName(server.getName());
        thread.start();

        if (!SHUTDOWN_HOOK_ACTIVE) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (!externalProcesses.isEmpty()) {
                    log.info("interrupting threads...");
                    externalProcesses.values().forEach(Process::destroy);
                    log.info("stopping threads...");
                    externalProcesses.values().forEach(Process::destroyForcibly);
                }
            }));
            SHUTDOWN_HOOK_ACTIVE = true;
        }
        assertThat(server.getStartupTimeoutSec())
            .withFailMessage("Startup time in sec is mandatory attribute!")
            .isNotNull();

        if (exception.get() != null) {
            throw new TigerTestEnvException("Unable to start external jar!", exception.get());
        }
        if (waitForService(server, server.getStartupTimeoutSec() * 500L, true)) {
            if (exception.get() != null) {
                throw new TigerTestEnvException("Unable to start external jar!", exception.get());
            }
            return;
        }
        if (exception.get() != null) {
            throw new TigerTestEnvException("Unable to start external jar!", exception.get());
        }
        waitForService(server, server.getStartupTimeoutSec() * 500L, false);
        if (exception.get() != null) {
            throw new TigerTestEnvException("Unable to start external jar!", exception.get());
        }
    }

    private String findJavaExecutable() {
        String[] paths = System.getenv("PATH").split(SystemUtils.IS_OS_WINDOWS ? ";" : ":");
        String javaProg = "java" + (SystemUtils.IS_OS_WINDOWS ? ".exe" : "");
        return Arrays.stream(paths)
            .map(path -> Path.of(path, javaProg).toFile())
            .filter(file -> file.exists() && file.canExecute())
            .map(File::getAbsolutePath)
            .findAny()
            .orElseThrow(() -> new TigerTestEnvException("Unable to find executable java program in PATH"));
    }


    private boolean waitForService(CfgServer server, long timeoutms, boolean quiet)
        throws IOException, InterruptedException {

        if (server.getHealthcheck() == null || server.getHealthcheck().equals("NONE")) {
            log.info("Waiting " + (timeoutms / 1000) + "s to get external server " + server.getName() + " online...");
            Thread.sleep(timeoutms);
            return true;
        }

        long startms = System.currentTimeMillis();
        if (!quiet) {
            log.info("  Checking external URL instance  " + server.getName() + " is available ...");
        }
        try {
            InsecureRestorableTrustAllManager.saveContext();
            InsecureRestorableTrustAllManager.allowAllSSL();
            while (System.currentTimeMillis() - startms < timeoutms) {
                var url = new URL(server.getHealthcheck());
                URLConnection con = url.openConnection();
                con.setConnectTimeout(1000);
                try {
                    con.connect();
                    log.info("External node " + server.getName() + " is online");
                    log.info(Ansi.BOLD + Ansi.GREEN + "External server Startup OK " + server.getSource().get(0)
                        + Ansi.RESET);
                    return true;
                } catch (ConnectException cex) {
                    if (!quiet) {
                        log.info("No connection...");
                    }
                } catch (SSLHandshakeException sslhe) {
                    log.warn(Ansi.YELLOW + "SSL handshake but server at least seems to be up!" + sslhe.getMessage()
                        + Ansi.RESET);
                    return true;
                } catch (SSLException sslex) {
                    if (sslex.getMessage().equals("Unsupported or unrecognized SSL message")) {
                        if (!quiet) {
                            log.error("Unsupported or unrecognized SSL message - MAYBE you mismatched http/httpS?");
                        }
                    } else {
                        if (!quiet) {
                            log.error("SSL Error - " + sslex.getMessage(), sslex);
                        }
                    }
                } catch (Exception e) {
                    if (!quiet) {
                        log.error("Failed to connect - " + e.getMessage(), e);
                    }
                }
                Thread.sleep(1000);
            }
            if (!quiet) {
                throw new TigerTestEnvException("Timeout waiting for external server to respond!");
            }
        } catch (InterruptedException ie) {
            log.warn("Interruption while waiting for external server to respond!", ie);
            Thread.currentThread().interrupt();
        } finally {
            InsecureRestorableTrustAllManager.restoreContext();
        }
        return false;
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
            if (System.currentTimeMillis() - startms > 15 * 60 * 1000) {
                t.interrupt();
                t.stop();
                throw new TigerTestEnvException("Download of " + jarUrl + " took longer then 15 minutes!");
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
        getConfiguration().getServers().stream()
            .filter(srv -> srv.getName().equals(server.getName()))
            .findAny()
            .orElseThrow(() -> new TigerTestEnvException("Unknown server '" + server.getName() + "'!"));
        final ServerType type = server.getType();
        if (type == ServerType.EXTERNALURL) {
            shutDownExternal(server);
        } else if (type == ServerType.DOCKER) {
            shutDownDocker(server);
        } else if (type == ServerType.EXTERNALJAR || type == ServerType.REVERSEPROXY) {
            shutDownJar(server);
        } else {
            // TODO docker compose
            throw new TigerTestEnvException("Unsupported server uri type " + type);
        }
    }

    private void loadPKIForProxy(final CfgServer srv) {
        log.info("  loading PKI resources for instance " + srv.getName() + "...");
        srv.getPkiKeys().stream()
            .filter(key -> key.getType().equals("cert"))
            .forEach(key -> {
                log.info("Adding certificate " + key.getId());
                getLocalTigerProxy().addKey(
                    key.getId(),
                    KeyMgr.readCertificateFromPem("-----BEGIN CERTIFICATE-----\n"
                        + key.getPem().replace(" ", "\n")
                        + "\n-----END CERTIFICATE-----").getPublicKey());
            });
        srv.getPkiKeys().stream()
            .filter(key -> key.getType().equals("key"))
            .forEach(key -> {
                log.info("Adding key " + key.getId());
                getLocalTigerProxy().addKey(
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
        if (proc != null) {
            log.info("interrupting thread for " + server.getName() + "...");
            proc.destroy();
            log.info("stopping thread for " + server.getName() + "...");
            proc.destroyForcibly();
        }
        removeRoute(server);
    }

    private void removeRoute(CfgServer server) {
        log.info("Removing routes for " + server.getName() + "...");
        Predicate<TigerRoute> isServerRoute = route -> route.getFrom().equals(HTTP + server.getName())
            || route.getFrom().equals(HTTPS + server.getName());
        routesList.stream()
            .filter(isServerRoute)
            .forEach(r -> localTigerProxy.removeRoute(r.getFrom()));
        routesList.removeIf(isServerRoute);
    }


    public List<TigerRoute> getRoutes() {
        return routesList;
    }
}
