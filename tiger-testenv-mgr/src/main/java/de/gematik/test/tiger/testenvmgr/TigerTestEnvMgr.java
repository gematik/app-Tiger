/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.banner.Banner;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.TigerProxyApplication;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import de.gematik.test.tiger.testenvmgr.env.*;
import de.gematik.test.tiger.testenvmgr.servers.TigerServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerLogListener;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerStatus;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.awt.HeadlessException;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

@Slf4j
@Getter
public class TigerTestEnvMgr implements ITigerTestEnvMgr, TigerEnvUpdateSender, TigerUpdateListener, DisposableBean {

    public static final String HTTP = "http://";
    public static final String HTTPS = "https://";
    public static final String CFG_PROP_NAME_LOCAL_PROXY_ADMIN_PORT = "tiger.tigerProxy.adminPort";
    public static final String CFG_PROP_NAME_LOCAL_PROXY_PROXY_PORT = "tiger.tigerProxy.proxyPort";
    private final Configuration configuration;
    private final DockerMgr dockerManager;
    private final Map<String, Object> environmentVariables;
    private final TigerProxy localTigerProxy;
    private final List<TigerRoute> routesList = new ArrayList<>();
    private final Map<String, TigerServer> servers = new HashMap<>();
    private final ExecutorService executor = Executors
        .newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    private final List<TigerUpdateListener> listeners = new ArrayList<>();
    private final DownloadManager downloadManager = new DownloadManager();
    private ServletWebServerApplicationContext localTigerProxyApplicationContext;

    private boolean userAcknowledgedShutdown = false;
    private boolean userAcknowledgedContinueTestRun = false;
    private boolean userAcknowledgedFailingTestRun = false;

    @Setter
    @Getter
    private boolean workflowUiSentFetch = false;

    public TigerTestEnvMgr() {
        Configuration configuration = readConfiguration();

        dockerManager = new DockerMgr();

        localTigerProxy = startLocalTigerProxy(configuration);

        if (configuration.isLocalProxyActive()) {
            log.info(Ansi.colorize("Local Tiger Proxy URL http://localhost:{}",
                RbelAnsiColors.BLUE_BOLD), localTigerProxy.getProxyPort());
            log.info(Ansi.colorize("Local Tiger Proxy UI http://localhost:{}/webui",
                RbelAnsiColors.BLUE_BOLD), localTigerProxyApplicationContext.getWebServer().getPort());
            environmentVariables = new HashMap<>(
                Map.of("PROXYHOST", "host.docker.internal",
                    "PROXYPORT", localTigerProxy.getProxyPort()));
        } else {
            log.info("Local Tiger Proxy deactivated");
            environmentVariables = new HashMap<>();
        }
        this.configuration = configuration;

        createServerObjects();

        log.info("Tiger Testenv mgr created OK");
    }

    private TigerProxy startLocalTigerProxy(Configuration configuration) {
        log.info("\n" + Banner.toBannerStr("STARTING LOCAL PROXY...", RbelAnsiColors.BLUE_BOLD.toString()));

        final TigerProxy localTigerProxy;
        if (configuration.getTigerProxy() == null) {
            configuration.setTigerProxy(TigerProxyConfiguration.builder().build());
        }
        TigerProxyConfiguration proxyConfig = configuration.getTigerProxy();
        proxyConfig.setSkipTrafficEndpointsSubscription(true);
        if (proxyConfig.getProxyRoutes() == null) {
            proxyConfig.setProxyRoutes(List.of());
        }
        if (proxyConfig.getTls().getServerRootCa() == null) {
            proxyConfig.getTls().setServerRootCa(new TigerConfigurationPkiIdentity(
                "CertificateAuthorityCertificate.pem;PKCS8CertificateAuthorityPrivateKey.pem;PKCS8"));
        }

        Map<String, Object> properties = new HashMap<>(TigerSerializationUtil.toMap(proxyConfig, "tigerProxy"));
        if (configuration.getTigerProxy().getAdminPort() == 0) {
            int port = TigerGlobalConfiguration.readIntegerOptional("tiger.internal.localproxy.admin.port")
                .orElseThrow(() -> new TigerEnvironmentStartupException("No free port reserved for local Tiger Proxy admin"));
            properties.put("server.port", Integer.toString(port));
        } else {
            properties.put("server.port", Integer.toString(configuration.getTigerProxy().getAdminPort()));
            TigerGlobalConfiguration.putValue("tiger.internal.localproxy.port",
                Integer.toString(configuration.getTigerProxy().getAdminPort()));
        }
        localTigerProxyApplicationContext = (ServletWebServerApplicationContext) new SpringApplicationBuilder()
            .bannerMode(Mode.OFF)
            .properties(properties)
            .sources(TigerProxyApplication.class)
            .web(WebApplicationType.SERVLET)
            .registerShutdownHook(false)
            .initializers()
            .run();

        localTigerProxy = localTigerProxyApplicationContext.getBean(TigerProxy.class);

        TigerGlobalConfiguration.putValue(CFG_PROP_NAME_LOCAL_PROXY_PROXY_PORT, localTigerProxy.getProxyPort());
        TigerGlobalConfiguration.putValue(CFG_PROP_NAME_LOCAL_PROXY_ADMIN_PORT,
            String.valueOf(localTigerProxyApplicationContext.getWebServer().getPort()));

        return localTigerProxy;
    }

    public static void waitForConsoleInput(String textToEnter) {
        Console c = System.console();
        String message = "\n" + Banner.toBannerStr("Press " + (textToEnter.isEmpty() ? "" : "'" + textToEnter + "' and ") + "ENTER.",
            RbelAnsiColors.RED_BOLD.toString());
        if (c != null) {
            String cmd = null;
            while (cmd == null || !cmd.equals(textToEnter)) {
                log.info(message);
                if (cmd != null) {
                    log.warn("Received: '{}'", cmd);
                }
                cmd = c.readLine();
                try {
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new TigerTestEnvException("Interrupt received while waiting for console input", e);
                }
            }
        } else {
            log.warn("No Console interface found, trying System in stream...");
            try {
                BufferedReader rdr = new BufferedReader(new InputStreamReader(System.in));
                String cmd = null;
                while (cmd == null || !cmd.equals(textToEnter)) {
                    log.info(message);
                    if (cmd != null) {
                        log.warn("Received: '{}'", cmd);
                    }
                    cmd = rdr.readLine();
                    try {
                        TimeUnit.MILLISECONDS.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new TigerTestEnvException("Interrupt received while waiting for console input", e);
                    }
                }
            } catch (IOException e) {
                log.warn("Unable to open input stream from console! Continuing with test run...", e);
            }
        }
        log.info("Step wait acknowledged. Continueing...");
    }

    private static void readTemplates() {
        // read configuration from file and templates from classpath resource
        try {
            final URL templatesUrl = TigerTestEnvMgr.class.getResource("templates.yaml");
            final String templatesYaml = IOUtils.toString(
                Objects.requireNonNull(templatesUrl).toURI(),
                StandardCharsets.UTF_8);
            TigerGlobalConfiguration.readTemplates(templatesYaml, "tiger", "servers");
        } catch (IOException | URISyntaxException e) {
            throw new TigerConfigurationException("Unable to read templates YAML!", e);
        }
    }

    private static Configuration readConfiguration() {
        TigerGlobalConfiguration.initialize();
        readTemplates();
        final Configuration configuration = TigerGlobalConfiguration.instantiateConfigurationBean(Configuration.class,
                "tiger")
            .orElseGet(Configuration::new);
        for (CfgServer cfgServer : configuration.getServers().values()) {
            if (StringUtils.isNotEmpty(cfgServer.getTemplate())) {
                throw new TigerConfigurationException("Could not resolve template '" + cfgServer.getTemplate() + "'");
            }
        }
        return configuration;
    }

    private void assertNoCyclesInGraph() {
        servers.values().forEach(server -> cycleChecker(server, new HashSet<>()));
    }

    private void cycleChecker(final TigerServer currentPosition, final Set<TigerServer> visitedServer) {
        if (visitedServer.contains(currentPosition)) {
            throw new TigerEnvironmentStartupException(
                "Cyclic graph detected in startup sequence: " + visitedServer.stream()
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

    private void createServerObjects() {
        for (Map.Entry<String, CfgServer> serverEntry : configuration.getServers().entrySet()) {
            if (serverEntry.getValue().isActive()) {
                final TigerServer server = TigerServer.create(serverEntry.getKey(), serverEntry.getValue(), this);
                servers.put(serverEntry.getKey(), server);
                server.registerNewListener(this);
            }
        }
    }

    @Override
    public void setUpEnvironment() {
        assertNoCyclesInGraph();
        assertNoUnknownServersInDependencies();

        final List<TigerServer> initialServersToBoot = servers.values().parallelStream()
            .filter(server -> server.getDependUponList().isEmpty())
            .collect(Collectors.toList());

        log.info("Booting following server(s): {}",
            initialServersToBoot.stream()
                .map(TigerServer::getHostname)
                .collect(Collectors.toList()));

        initialServersToBoot.parallelStream()
            .forEach(this::startServer);

        localTigerProxy.subscribeToTrafficEndpoints(configuration.getTigerProxy());

        log.info(Ansi.colorize("Finished set up test environment OK", RbelAnsiColors.GREEN_BOLD));
    }

    private void assertNoUnknownServersInDependencies() {
        getServers().values().forEach(TigerServer::getDependUponList);
    }

    private void startServer(TigerServer server) {
        synchronized (server) { //NOSONAR
            // we REALLY want to synchronize ONLY on the server!
            if (server.getStatus() != TigerServerStatus.NEW) {
                return;
            }
            server.start(this);
        }

        servers.values().parallelStream()
            .peek(toBeStartedServer -> log.debug("Considering to start server {} with status {}...",
                toBeStartedServer.getServerId(), toBeStartedServer.getStatus()))
            .filter(candidate -> candidate.getStatus() == TigerServerStatus.NEW)
            .filter(candidate -> candidate.getDependUponList().stream()
                .filter(depending -> depending.getStatus() != TigerServerStatus.RUNNING)
                .findAny().isEmpty())
            .peek(toBeStartedServer -> log.info("Starting server {} with status {}",
                toBeStartedServer.getServerId(), toBeStartedServer.getStatus()))
            .forEach(this::startServer);
    }

    public String replaceSysPropsInString(String str) {
        return str;
    }

    @Override
    public void shutDown() {
        log.info(Ansi.colorize("Shutting down all servers...", RbelAnsiColors.RED_BOLD));
        servers.values().forEach(TigerServer::shutdown);

        log.info(Ansi.colorize("Shutting down local tiger proxy...", RbelAnsiColors.RED_BOLD));
        localTigerProxy.shutdown();
        localTigerProxyApplicationContext.close();

        log.info(Ansi.colorize("Finished shutdown test environment OK", RbelAnsiColors.RED_BOLD));
    }

    public void receiveTestEnvUpdate(TigerStatusUpdate statusUpdate) {
        listeners.forEach(listener -> listener.receiveTestEnvUpdate(statusUpdate));
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

    public boolean isLocalTigerProxyActive() {
        if (configuration == null) {
            return true;
        }
        return configuration.isLocalProxyActive();
    }

    @Override
    public void registerNewListener(TigerUpdateListener listener) {
        listeners.add(listener);
    }

    @Override
    public void registerLogListener(TigerServerLogListener listener) {
        // do nothing here
    }


    public static void openWorkflowUiInBrowser(String adminPort) {
        try {
            String url = "http://localhost:" + adminPort;

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Action.BROWSE)) {
                Desktop desktop = Desktop.getDesktop();
                log.info("Starting Workflow UI via Java Desktop API");
                desktop.browse(new URI(url));
                log.info(Ansi.colorize("Workflow UI {}", RbelAnsiColors.BLUE_BOLD), url);
            } else {
                String command;
                String operatingSystemName = System.getProperty("os.name").toLowerCase();
                if (operatingSystemName.contains("nix") || operatingSystemName.contains("nux")) {
                    command = "xdg-open " + url;
                } else if (operatingSystemName.contains("win")) {
                    command = "rundll32 url.dll,FileProtocolHandler " + url;
                } else if (operatingSystemName.contains("mac")) {
                    command = "open " + url;
                } else {
                    log.error("Unknown operation system '{}'", operatingSystemName);
                    return;
                }
                log.info("Starting Workflow UI via '{}'", command);
                Runtime.getRuntime().exec(command);
                log.info(Ansi.colorize("Workflow UI " + url, RbelAnsiColors.BLUE_BOLD));
            }
        } catch (HeadlessException hex) {
            log.error("Unable to start Workflow UI on a headless server!", hex);
        } catch (RuntimeException | URISyntaxException | IOException e) {
            log.error("Exception while trying to start browser for Workflow UI, still continuing with test run", e);
        }
    }

    public void receivedUserAcknowledgementForShutdown() {
        userAcknowledgedShutdown = true;
    }

    public void receivedResumeTestRunExecution() {
        userAcknowledgedContinueTestRun = true;
    }

    public void receivedCancelTestRunExecution() {
        userAcknowledgedFailingTestRun = true;
    }

    public void resetUserInput() {
        userAcknowledgedContinueTestRun = false;
        userAcknowledgedFailingTestRun = false;
    }

    @Override
    public void destroy() throws Exception {
        shutDown();
    }
}
