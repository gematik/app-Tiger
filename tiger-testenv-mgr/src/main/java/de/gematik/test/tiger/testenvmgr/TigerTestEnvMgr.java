/*
 * Copyright (c) 2021 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.testenvmgr;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.OsEnvironment;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.common.banner.Banner;
import de.gematik.test.tiger.common.config.CfgExternalJarOptions;
import de.gematik.test.tiger.common.config.CfgTigerProxyOptions;
import de.gematik.test.tiger.common.config.ServerType;
import de.gematik.test.tiger.common.config.TigerConfigurationHelper;
import de.gematik.test.tiger.common.pki.KeyMgr;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.testenvmgr.config.*;
import de.gematik.test.tiger.testenvmgr.config.tigerProxyStandalone.CfgStandaloneProxy;
import de.gematik.test.tiger.testenvmgr.config.tigerProxyStandalone.CfgStandaloneServer;
import java.io.*;
import java.lang.reflect.Method;
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
        log.info(Ansi.colorize("finished set up test environment OK", Ansi.BOLD + Ansi.GREEN));
    }

    @Override
    public List<CfgServer> getTestEnvironment() {
        return configuration.getServers();
    }

    @Override
    public void start(final CfgServer server, Configuration configuration) throws RuntimeException {
        if (!server.isActive()) {
            log.warn("skipping inactive server " + server.getName());
            return;
        }

        checkCfgProperties(server);

        final ServerType type = server.getType();
        if (configuration.isLocalProxyActive()) {
            environmentVariables.put("PROXYPORT", localTigerProxy.getPort());
            environmentVariables.put("PROXYHOST", type == ServerType.DOCKER ? "host.docker.internel" : "127.0.0.1");
        }

        // replace sys props in environment
        if (server.getEnvironment() != null) {
            server.setEnvironment(server.getEnvironment().stream()
                .map(this::replaceSysPropsInString)
                .collect(Collectors.toList()));
        }

        // apply routes to local proxy
        if (server.getUrlMappings() != null) {
            server.getUrlMappings().forEach(mapping -> {
                String[] kvp = mapping.split(" --> ", 2);
                localTigerProxy.addRoute(TigerRoute.builder()
                    .from(kvp[0])
                    .to(kvp[1])
                    .build());
            });
        }

        try {
            switch (type) {
                case DOCKER:
                case DOCKER_COMPOSE:
                    startDocker(server, configuration);
                    break;
                case EXTERNALURL:
                    initializeExternalUrl(server);
                    break;
                case TIGERPROXY:
                    initializeTigerProxy(server, configuration);
                    break;
                case EXTERNALJAR:
                    initializeExternalJar(server);
                    break;
                default:
                    throw new TigerTestEnvException(
                        String.format("Unsupported server type %s found in server %s", type, server.getName()));
            }
        } catch (URISyntaxException | InterruptedException | IOException use) {
            throw new TigerTestEnvException("Failed to start server " + server.getName(), use);
        }
        server.setStarted(true);

        loadPKIForProxy(server);

        // set system properties from exports section and store the value in environmentVariables map
        // replace ${NAME} with server name
        // TODO once we did server list -> server map replace with HOSTNAME of server
        //
        server.getExports().forEach(exp -> {
            String[] kvp = exp.split("=", 2);
            // ports substitution are only supported for docker based instances
            if (type == ServerType.DOCKER && server.getDockerOptions().getPorts() != null) {
                server.getDockerOptions().getPorts().forEach((localPort, externPort) ->
                    kvp[1] = kvp[1].replace("${PORT:" + localPort + "}", String.valueOf(externPort))
                );
            }
            kvp[1] = kvp[1].replace("${NAME}", server.getName());

            log.info("  setting system property " + kvp[0] + "=" + kvp[1]);
            System.setProperty(kvp[0], kvp[1]);
            environmentVariables.put(kvp[0], kvp[1]);
        });


    }

    public void checkCfgProperties(CfgServer server) {
        final ServerType type = server.getType();
        assertCfgPropertySet(server, "name");
        assertCfgPropertySet(server, "type");

        if (type != ServerType.EXTERNALJAR && type != ServerType.EXTERNALURL && type != ServerType.DOCKER_COMPOSE) {
            assertCfgPropertySet(server, "version");
        }

        // set default value for Tiger Proxy source
        if (server.getType() == ServerType.TIGERPROXY && (server.getSource() == null || server.getSource().isEmpty())) {
            log.info("Defaulting tiger proxy source to gematik nexus for " + server.getName());
            server.setSource(new ArrayList<>(List.of("nexus")));
        }
        // set default value for Tiger Proxy external Jar options
        if (server.getType() == ServerType.TIGERPROXY && server.getExternalJarOptions() == null) {
            server.setExternalJarOptions(new CfgExternalJarOptions());
        }

        // set default value for Tiger Proxy healthcheck
        if (server.getType() == ServerType.TIGERPROXY && server.getExternalJarOptions().getHealthcheck() == null) {
            server.getExternalJarOptions()
                .setHealthcheck("http://127.0.0.1:" + server.getTigerProxyCfg().getServerPort());
        }

        // set default values for all types
        if (server.getStartupTimeoutSec() == null) {
            log.info("Defaulting startup timeout sec to 20sec for server " + server.getName());
            server.setStartupTimeoutSec(20);
        }

        // defaulting work dir to temp folder on system
        if (type == ServerType.EXTERNALJAR || type == ServerType.TIGERPROXY) {
            if (server.getExternalJarOptions().getWorkingDir() == null) {
                File folder = Path.of(System.getProperty("java.io.tmpdir"), "tiger_downloads").toFile();
                log.info("Defaulting to temp folder '" + folder.getAbsolutePath() + "' as work dir for server "
                    + server.getName());
                if (!folder.exists()) {
                    if (!folder.mkdirs()) {
                        throw new TigerTestEnvException("Unable to create temp folder " + folder.getAbsolutePath());
                    }
                }
                server.getExternalJarOptions().setWorkingDir(folder.getAbsolutePath());
            }
        }

        if (type != ServerType.TIGERPROXY) {
            assertCfgPropertySet(server, "source");
        }

        if (type == ServerType.EXTERNALJAR) {
            assertCfgPropertySet(server.getExternalJarOptions(), "healthcheck");
        }

        if (type == ServerType.TIGERPROXY) {
            if (server.getTigerProxyCfg().getServerPort() < 1) {
                throw new TigerTestEnvException("Server port for Tiger Proxy must be explicitly set!");
            }
        }
    }

    @SneakyThrows
    private void assertCfgPropertySet(Object srv, String propName) {
        Method mthd = srv.getClass()
            .getMethod("get" + Character.toUpperCase(propName.charAt(0)) + propName.substring(1));
        Object value = mthd.invoke(srv);
        if (value == null) {
            throw new TigerTestEnvException("Server " + propName + " must be set and must not be NULL!");
        }
        if (value instanceof List) {
            List<Object> l = (List) value;
            if (l.isEmpty() ||
                l.get(0) == null) {
                throw new TigerTestEnvException(
                    "Server " + propName + " list must be set and must contain at least one not empty entry!");
            }
            if (l.get(0) instanceof String) {
                if (((String) l.get(0)).isBlank()) {
                    throw new TigerTestEnvException(
                        "Server " + propName + " list must be set and must contain at least one not empty entry!");
                }
            }
        } else {
            if (value instanceof String) {
                if (((String) value).isBlank()) {
                    throw new TigerTestEnvException("Server " + propName + " must be set and must not be empty!");
                }
            }
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
        log.info(Ansi.colorize(
            "Starting docker container for " + server.getName() + ":" + server.getSource().get(0),
            Ansi.BOLD + Ansi.GREEN));

        if (server.getType() == ServerType.DOCKER) {
            dockerManager.startContainer(server, configuration, this);
        } else {
            dockerManager.startComposition(server);
        }

        // add routes needed for each server to local docker proxy
        // ATTENTION only one route per server!
        if (server.getDockerOptions().getPorts() != null && !server.getDockerOptions().getPorts().isEmpty()) {
            routesList.add(TigerRoute.builder()
                .from("http://" + server.getName())
                .to("http://localhost:" + server.getDockerOptions().getPorts().values().iterator().next())
                .build());
            localTigerProxy.addRoute(TigerRoute.builder()
                .from("http://" + server.getName())
                .to("http://localhost:" + server.getDockerOptions().getPorts().values().iterator().next())
                .build());
        }
        log.info(Ansi.colorize(
            "Docker container Startup for " + server.getName() + " : " + server.getSource().get(0) + " OK",
            Ansi.BOLD + Ansi.GREEN));
    }

    @SneakyThrows
    private void initializeTigerProxy(CfgServer server, Configuration configuration) {
        CfgTigerProxyOptions reverseProxyCfg = server.getTigerProxyCfg();

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

        reverseProxyCfg.getProxyCfg().getProxyRoutes().forEach(route -> {
            route.setFrom(replaceSysPropsInString(route.getFrom()));
            route.setTo(replaceSysPropsInString(route.getTo()));
        });

        final String downloadUrl;
        final String jarFile = "tiger-standalone-proxy-" + server.getVersion() + ".jar";
        if (server.getSource().get(0).equals("nexus")) {
            downloadUrl =
                "https://build.top.local/nexus/service/local/repositories/releases/content/de/gematik/test/tiger-standalone-proxy/"
                    + server.getVersion() + "/" + jarFile;
        } else if (server.getSource().get(0).equals("maven")) {
            downloadUrl = "https://repo1.maven.org/maven2/de/gematik/test/tiger-standalone-proxy/"
                + server.getVersion() + "/" + jarFile;
        } else {
            downloadUrl = server.getSource().get(0);
        }
        final File folder = new File(server.getExternalJarOptions().getWorkingDir());
        server.setSource(List.of(downloadUrl));
        if (server.getExternalJarOptions().getHealthcheck() == null) {
            server.getExternalJarOptions().setHealthcheck("http://127.0.0.1:" + reverseProxyCfg.getServerPort());
        }
        if (server.getExternalJarOptions().getArguments() == null) {
            server.getExternalJarOptions().setArguments(new ArrayList<>());
        }
        server.getExternalJarOptions().getArguments().add("--spring.profiles.active=" + server.getName());

        ObjectMapper om = new ObjectMapper(new YAMLFactory());
        om.writeValue(Path.of(folder.getAbsolutePath(), "application-" + server.getName() + ".yaml").toFile(),
            standaloneCfg);

        initializeExternalJar(server);
    }

    public String replaceSysPropsInString(String str) {
        return TokenSubstituteHelper.substitute(str, "", environmentVariables);
    }

    private void getDestinationUrlFromProxiedServer(CfgServer server, Configuration configuration,
        CfgTigerProxyOptions cfg) {
        final String destUrl;
        CfgServer proxiedServer = configuration.getServers().stream()
            .filter(srv -> srv.getName().equals(cfg.getProxiedServer()))
            .findAny().orElseThrow(
                () -> new TigerTestEnvException(
                    "Proxied server '" + cfg.getProxiedServer() + "' not found in list!"));
        switch (proxiedServer.getType()) {
            case DOCKER:
                if (proxiedServer.getExternalJarOptions().getHealthcheck() == null) {
                    if (!proxiedServer.isStarted()) {
                        throw new TigerTestEnvException("If reverse proxy is to be used with docker container '"
                            + proxiedServer.getName()
                            + "' make sure to start it first or have a valid healthcheck setting!");
                    } else {
                        destUrl =
                            cfg.getProxyProtocol() + "://127.0.0.1:" + server.getDockerOptions().getPorts().values()
                                .iterator().next();
                    }
                } else {
                    destUrl = proxiedServer.getExternalJarOptions().getHealthcheck();
                }
                break;
            case EXTERNALJAR:
                assertThat(proxiedServer.getExternalJarOptions().getHealthcheck())
                    .withFailMessage(
                        "To be proxied server '" + proxiedServer.getName() + "' has no valid healthcheck Url")
                    .isNotBlank();
                destUrl = proxiedServer.getExternalJarOptions().getHealthcheck();
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
        log.info(Ansi.colorize("starting external URL instance " + server.getName() + "...", Ansi.BOLD + Ansi.GREEN));
        final var url = new URL(replaceSysPropsInString(server.getSource().get(0)));
        addServerToLocalProxyRouteMap(server, url);

        log.info("  Waiting 50% of start up time for external URL instance  " + server.getName() + " to come up ...");

        if (waitForService(server, server.getStartupTimeoutSec() * 500L, true)) {
            return;
        }
        waitForService(server, server.getStartupTimeoutSec() * 500L, false);
    }

    public void initializeExternalJar(final CfgServer server)
        throws RuntimeException, InterruptedException, IOException, URISyntaxException {

        log.info(Ansi.colorize("starting external jar instance " + server.getName() + " in folder " + server
            .getExternalJarOptions().getWorkingDir() + "...", Ansi.BOLD + Ansi.GREEN));

        log.info("preparing check for external jar location...");
        var jarUrl = server.getSource().get(0);
        var jarName = jarUrl.substring(jarUrl.lastIndexOf("/") + 1);
        var jarFile = Paths.get(server.getExternalJarOptions().getWorkingDir(), jarName).toFile();

        log.info("checking external jar location: " + jarUrl + "," + jarName + "," + jarFile.getAbsolutePath());
        if (!jarFile.exists()) {
            if (jarUrl.startsWith("local:")) {
                throw new TigerTestEnvException("Local jar " + jarFile.getAbsolutePath() + " not found!");
            }
            downloadJar(server, jarUrl, jarFile);
        }

        log.info("creating cmd line...");
        List<String> options = server.getExternalJarOptions().getOptions().stream()
            .map(this::replaceSysPropsInString)
            .collect(Collectors.toList());
        String javaExe = findJavaExecutable();
        options.add(0, javaExe);
        options.add("-jar");
        options.add(jarName);
        options.addAll(server.getExternalJarOptions().getArguments());
        log.info("executing '" + String.join(" ", options));
        log.info("in working dir: " + new File(server.getExternalJarOptions().getWorkingDir()).getAbsolutePath());
        final AtomicReference<Process> proc = new AtomicReference<>();
        RuntimeException throwing = null;
        try {
            final AtomicReference<Throwable> exception = new AtomicReference<>();
            var thread = new Thread(() -> {
                Process p = null;
                try {
                    p = new ProcessBuilder()
                        .command(options.toArray(String[]::new))
                        .directory(new File(server.getExternalJarOptions().getWorkingDir()))
                        .inheritIO()
                        .start();
                } catch (Throwable t) {
                    log.error("Failed to start process", t);
                    exception.set(t);
                }
                externalProcesses.put(server.getName(), p);
                proc.set(p);
                log.info("Proc set in atomic var " + p);
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

            if (server.getExternalJarOptions().getHealthcheck().equals("NONE")) {
                log.warn("Healthcheck is configured as NONE, so unable to add route to local proxy!");
            } else {
                URL url = new URL(server.getExternalJarOptions().getHealthcheck());
                addServerToLocalProxyRouteMap(server, url);
            }

            if (exception.get() != null) {
                throwing = new TigerTestEnvException("Unable to start external jar!", exception.get());
            }
            if (waitForService(server, server.getStartupTimeoutSec() * 500L, true)) {
                if (exception.get() != null) {
                    throwing = new TigerTestEnvException("Unable to start external jar!", exception.get());
                }
            } else {
                if (exception.get() != null) {
                    throwing = new TigerTestEnvException("Unable to start external jar!", exception.get());
                }
                waitForService(server, server.getStartupTimeoutSec() * 500L, false);
                if (exception.get() != null) {
                    throwing = new TigerTestEnvException("Unable to start external jar!", exception.get());
                }
            }
        } finally {
            log.info("proc: " + proc.get());
            if (proc.get() != null) {
                if (proc.get().isAlive()) {
                    log.info("Started " + server.getName());
                } else if (proc.get().exitValue() == 0) {
                    log.info("Process exited already " + server.getName());
                } else {
                    log.info("Unclear process state" + proc);
                    log.info("Output from cmd: " + IOUtils.toString(proc.get().getInputStream(), StandardCharsets.UTF_8));
                }
            } else {
                if (throwing == null) {
                    throwing = new TigerTestEnvException("External Jar startup failed");
                } else {
                    throwing = new TigerTestEnvException("External Jar startup failed", throwing);
                }
            }
        }
        if (throwing != null) {
            throw throwing;
        }
    }

    private void addServerToLocalProxyRouteMap(CfgServer server, URL url) throws URISyntaxException {
        int port = url.getPort();
        if (port == -1) {
            port = url.getDefaultPort();
        }
        localTigerProxy.addRoute(TigerRoute.builder()
            .from("http://" + server.getName())
            .to(url.toURI().getScheme() + "://" + url.getHost() + ":" + port)
            .build());
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

        if (server.getExternalJarOptions().getHealthcheck() == null || server.getExternalJarOptions().getHealthcheck()
            .equals("NONE")) {
            log.info("Waiting " + (timeoutms / 1000) + "s to get external server " + server.getName() + " online...");
            Thread.sleep(timeoutms);
            return true;
        }

        long startms = System.currentTimeMillis();
        if (!quiet) {
            log.info("  Checking " + server.getType() + " instance '" + server.getName() + "' is available ...");
        }
        try {
            InsecureRestorableTrustAllManager.saveContext();
            InsecureRestorableTrustAllManager.allowAllSSL();
            while (System.currentTimeMillis() - startms < timeoutms) {
                var url = new URL(server.getExternalJarOptions().getHealthcheck());
                URLConnection con = url.openConnection();
                con.setConnectTimeout(1000);
                try {
                    con.connect();
                    log.info("External node " + server.getName() + " is online");
                    log.info(Ansi.BOLD + Ansi.GREEN + "External server Startup OK " + server.getSource().get(0)
                        + Ansi.RESET);
                    return true;
                } catch (ConnectException | SocketTimeoutException cex) {
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
                throw new TigerTestEnvException("Timeout waiting for external server to respond at '"
                    + server.getExternalJarOptions().getHealthcheck() + "'!");
            }
        } catch (InterruptedException ie) {
            log.warn("Interruption while waiting for external server to respond at '"
                + server.getExternalJarOptions().getHealthcheck() + "'!", ie);
            Thread.currentThread().interrupt();
        } finally {
            InsecureRestorableTrustAllManager.restoreContext();
        }
        return false;
    }


    private void downloadJar(CfgServer server, String jarUrl, File jarFile) throws InterruptedException {
        log.info("downloading jar for external server from " + jarUrl + "...");
        var workDir = new File(server.getExternalJarOptions().getWorkingDir());
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
            if (progressCtr == 8) {
                log.info("downloaded jar for " + server.getName() + "  " + jarFile.length() / 1000 + " kb");
                progressCtr = 0;
            }
        }
    }

    @Override
    public void shutDown(final CfgServer server) {
        log.info("Shutting down server '" + server.getName() + "'");
        getConfiguration().getServers().stream()
            .filter(srv -> srv.getName().equals(server.getName()))
            .findAny()
            .orElseThrow(() -> new TigerTestEnvException("Unknown server '" + server.getName() + "'!"));
        final ServerType type = server.getType();
        if (type == ServerType.EXTERNALURL) {
            shutDownExternal(server);
        } else if (type == ServerType.DOCKER) {
            shutDownDocker(server);
        } else if (type == ServerType.EXTERNALJAR || type == ServerType.TIGERPROXY) {
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
        removeRoute(server);
        dockerManager.stopContainer(server);
    }

    private void shutDownExternal(final CfgServer server) {
        removeRoute(server);
    }

    private void shutDownJar(final CfgServer server) {
        log.info("Stopping external jar " + server.getName() + "...");
        removeRoute(server);
        Process proc = externalProcesses.get(server.getName());
        if (proc != null) {
            log.info("interrupting thread for " + server.getName() + "...");
            proc.destroy();
            log.info("stopping thread for " + server.getName() + "...");
            proc.destroyForcibly();
        } else {
            log.warn("Process for server " + server.getName() + " not found... No need to shutdown");
        }
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
