/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.OsEnvironment;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.common.banner.Banner;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerConfigurationHelper;
import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import de.gematik.test.tiger.testenvmgr.servers.TigerServer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.io.*;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;

@Slf4j
@Getter
public class TigerTestEnvMgr implements ITigerTestEnvMgr {

    public static final String HTTP = "http://";
    public static final String HTTPS = "https://";
    private final Configuration configuration;
    private final DockerMgr dockerManager;
    private final Map<String, Object> environmentVariables;
    private final TigerProxy localTigerProxy;
    private final List<TigerRoute> routesList = new ArrayList<>();
    private final Map<String, TigerServer> servers = new HashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

    public TigerTestEnvMgr() {
        Configuration configuration = readConfiguration();

        dockerManager = new DockerMgr();

        if (configuration.getTigerProxy() == null) {
            configuration.setTigerProxy(TigerProxyConfiguration.builder().build());
        }
        TigerProxyConfiguration proxyConfig = configuration.getTigerProxy();
        if (proxyConfig.getProxyRoutes() == null) {
            proxyConfig.setProxyRoutes(List.of());
        }
        if (proxyConfig.getTls().getServerRootCa() == null) {
            proxyConfig.getTls().setServerRootCa(new TigerConfigurationPkiIdentity(
                "CertificateAuthorityCertificate.pem;PKCS8CertificateAuthorityPrivateKey.pem;PKCS8"));
        }
        localTigerProxy = new TigerProxy(configuration.getTigerProxy());
        if (configuration.isLocalProxyActive()) {
            log.info("Starting local docker tiger proxy on port " + localTigerProxy.getPort() + "...");
            environmentVariables = new HashMap<>(
                Map.of("PROXYHOST", "host.docker.internal",
                    "PROXYPORT", localTigerProxy.getPort()));
        } else {
            log.info("Local docker tiger proxy deactivated");
            environmentVariables = new HashMap<>();
        }
        this.configuration = configuration;

        createServerObjects();

        log.info("Tiger Testenv mgr created OK");
    }

    public static void main(String[] args) {

        TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        try {
            envMgr.setUpEnvironment();
        } catch (Exception e) {
            log.error("Error while starting up stand alone tiger testenv mgr! ABORTING...", e);
            System.exit(1);
        }
        log.info(
            "\n" + Banner.toBannerStr("Tiger standalone test environment UP!", RbelAnsiColors.GREEN_BOLD.toString()));
        waitForQuit("TIGER standalone test environment");
        envMgr.shutDown();
        System.exit(0);
    }

    public static void waitForQuit(String appName) {
        Console c = System.console();
        if (c != null) {
            c.format("\n\n\nPress 'quit' and ENTER to stop " + appName + ".\n\n\n\n\n");
            String cmd = "";
            while (!cmd.equals("quit")) {
                cmd = c.readLine();
            }
            log.info("Stopping " + appName + "...");
        } else {
            log.warn("No Console interface found, trying System in stream...");
            log.info("\n\n\nPress 'quit' and ENTER to stop " + appName + ".\n\n\n\n\n");
            try {
                BufferedReader rdr = new BufferedReader(
                    new InputStreamReader(System.in));
                String cmd = "";
                while (!cmd.equals("quit")) {
                    cmd = rdr.readLine();
                }
            } catch (IOException e) {
                log.warn(
                    "Unable to open input stream from console! Running " + appName + " for max. 24 hours."
                        + "You will have to use Ctrl+C and eventually clean up the processes manually!");
                await().atMost(24, TimeUnit.HOURS).pollDelay(1, TimeUnit.SECONDS).until(() -> false);
            }
        }
    }

    public static void applyTemplates(JSONObject jsonCfg) {
        // read configuration from file and templates from classpath resource
        try {
            JSONObject jsonTemplate = TigerConfigurationHelper.yamlStringToJson(
                IOUtils.toString(Objects.requireNonNull(TigerTestEnvMgr.class.getResource(
                    "templates.yaml")).toURI(), StandardCharsets.UTF_8));
            TigerConfigurationHelper.applyTemplate(
                jsonCfg.getJSONObject("servers"), "template",
                jsonTemplate.getJSONArray("templates"), "templateName");
        } catch (IOException | URISyntaxException e) {
            throw new TigerConfigurationException("Unable to read templates YAML!", e);
        }
    }

    private static String getComputerName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return InetAddress.getLoopbackAddress().getHostName();
        }
    }

    private void assertNoCyclesInGraph() {
        servers.values().forEach(server -> cycleChecker(server, new HashSet<>()));
    }

    private void cycleChecker(final TigerServer currentPosition, final Set<TigerServer> visitedServer) {
        if (visitedServer.contains(currentPosition)) {
            throw new TigerEnvironmentStartupException("Cyclic graph detected in startup sequence: " + visitedServer.stream()
                .map(TigerServer::getServerId)
                .collect(Collectors.toList()));
        }
        if (currentPosition.getDependUponList().isEmpty()) {
            System.out.println(visitedServer);
            return;
        }
        for (TigerServer server : currentPosition.getDependUponList()) {
            var newSet = new HashSet<>(visitedServer);
            newSet.add(currentPosition);
            cycleChecker(server, newSet);
        }
    }

    private Configuration readConfiguration() {
        JSONObject jsonCfg = readConfiguredConfigurationFileAndConvert();
        applyTemplates(jsonCfg);
        TigerConfigurationHelper.overwriteWithSysPropsAndEnvVars("TIGER_TESTENV", "tiger.testenv", jsonCfg);

        return new TigerConfigurationHelper<Configuration>()
            .jsonStringToConfig(jsonCfg.toString(), Configuration.class);
    }

    private JSONObject readConfiguredConfigurationFileAndConvert() {
        var cfgFile = new File(OsEnvironment.getAsString(
            "TIGER_TESTENV_CFGFILE", "tiger-testenv-" + getComputerName() + ".yaml"));
        if (!cfgFile.exists()) {
            log.warn("Unable to read configuration from {}", cfgFile.getAbsolutePath());
            cfgFile = new File("tiger-testenv.yaml");
        }
        log.info("Reading configuration from {}...", cfgFile.getAbsolutePath());
        JSONObject jsonCfg = TigerConfigurationHelper.yamlToJson(cfgFile.getAbsolutePath());
        return jsonCfg;
    }

    private void createServerObjects() {
        for (Map.Entry<String, CfgServer> serverEntry : configuration.getServers().entrySet()) {
            if (!serverEntry.getValue().isActive()) {
                continue;
            }

            servers.put(serverEntry.getKey(),
                TigerServer.create(serverEntry.getKey(), serverEntry.getValue(), this));
        }
    }

    @Override
    public void setUpEnvironment() {
        assertNoCyclesInGraph();
        assertNoUnknownServersInDependencies();

        log.info("starting set up of test environment...");

        servers.values().parallelStream()
            .filter(server -> server.getDependUponList().isEmpty())
            .forEach(this::startServer);

        log.info(Ansi.colorize("finished set up test environment OK", RbelAnsiColors.GREEN_BOLD));
    }

    private void assertNoUnknownServersInDependencies() {
        getServers().values().stream()
            .forEach(TigerServer::getDependUponList);
    }

    private void startServer(TigerServer server) {
        synchronized (server) {
            if (server.isStarted()) {
                return;
            }
            server.start(this);
        }

        servers.values().parallelStream()
            .filter(candidate -> !candidate.isStarted())
            .filter(candidate -> candidate.getDependUponList().stream()
                .filter(depending -> !depending.isStarted())
                .findAny().isEmpty())
            .forEach(this::startServer);
    }

    public String replaceSysPropsInString(String str) {
        return TokenSubstituteHelper.substitute(str, "", environmentVariables);
    }

    @Override
    public void shutDown() {
        log.info("Shutting down server all servers...");
        servers.values().stream().forEach(TigerServer::shutdown);
    }

    public List<TigerRoute> getRoutes() {
        return servers.values().stream()
            .map(TigerServer::getRoutes)
            .flatMap(List::stream)
            .collect(Collectors.toUnmodifiableList());
    }

    public Optional<TigerServer> findServer(String serverName) {
        return Optional.ofNullable(servers.get(serverName));
    }
}
