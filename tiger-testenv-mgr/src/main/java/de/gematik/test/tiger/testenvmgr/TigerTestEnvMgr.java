/*
 * Copyright (c) 2022 gematik GmbH
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

import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.banner.Banner;
import de.gematik.test.tiger.common.config.SourceType;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.TigerProxyApplication;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import de.gematik.test.tiger.testenvmgr.env.DownloadManager;
import de.gematik.test.tiger.testenvmgr.env.TigerEnvUpdateSender;
import de.gematik.test.tiger.testenvmgr.env.TigerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.env.TigerUpdateListener;
import de.gematik.test.tiger.testenvmgr.servers.AbstractTigerServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerLogListener;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerStatus;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerType;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.awt.HeadlessException;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

@Slf4j
@Getter
public class TigerTestEnvMgr implements ITigerTestEnvMgr, TigerEnvUpdateSender, TigerUpdateListener, DisposableBean,
    AutoCloseable {

    public static final String HTTP = "http://";
    public static final String HTTPS = "https://";
    public static final String CFG_PROP_NAME_LOCAL_PROXY_ADMIN_PORT = "tiger.tigerProxy.adminPort";
    public static final String CFG_PROP_NAME_LOCAL_PROXY_PROXY_PORT = "tiger.tigerProxy.proxyPort";
    private final Configuration configuration;
    private final Map<String, Object> environmentVariables;
    private final TigerProxy localTigerProxy;
    private final List<TigerRoute> routesList = new ArrayList<>();
    private final Map<String, AbstractTigerServer> servers = new HashMap<>();
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

    private final Map<String, Class<? extends AbstractTigerServer>> serverClasses = new HashMap<>();

    public TigerTestEnvMgr() {
        this.configuration = readConfiguration();
        this.environmentVariables = new HashMap<>();

        lookupServerPluginsInClasspath();

        localTigerProxy = startLocalTigerProxy(configuration);
        try {
            if (configuration.isLocalProxyActive()) {
                log.info(Ansi.colorize("Local Tiger Proxy URL http://localhost:{}",
                    RbelAnsiColors.BLUE_BOLD), localTigerProxy.getProxyPort());
                log.info(Ansi.colorize("Local Tiger Proxy UI http://localhost:{}/webui",
                    RbelAnsiColors.BLUE_BOLD), localTigerProxyApplicationContext.getWebServer().getPort());
                environmentVariables.put("PROXYHOST", "host.docker.internal");
                environmentVariables.put("PROXYPORT", localTigerProxy.getProxyPort());
            } else {
                log.info("Local Tiger Proxy deactivated");
            }

            createServerObjects();

            log.info("Tiger Testenv mgr created OK");
        } catch (RuntimeException e) {
            shutDown();
            throw e;
        }
    }

    private void lookupServerPluginsInClasspath() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TigerServerType.class));
        Set<BeanDefinition> serverBeanDefinitions = scanner.findCandidateComponents("de.gematik.test.tiger");

        for (BeanDefinition serverBeanDefinition : serverBeanDefinitions) {
            try {
                Class<?> clz = Class.forName(serverBeanDefinition.getBeanClassName());
                String typeToken = clz.getAnnotation(TigerServerType.class).value();
                serverClasses.put(typeToken, clz.asSubclass(AbstractTigerServer.class));
                log.info("Registered server type {} with class {}", typeToken, serverBeanDefinition.getBeanClassName());
            } catch (ClassNotFoundException e) {
                throw new TigerTestEnvException("Unable to instantiate / find class "
                    + serverBeanDefinition.getBeanClassName() + " for server", e);
            }
        }
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

        Map<String, Object> properties = new HashMap<>(TigerSerializationUtil.toMap(proxyConfig, "tigerProxy"));
        if (configuration.getTigerProxy().getAdminPort() == 0) {
            int port = TigerGlobalConfiguration.readIntegerOptional("tiger.internal.localproxy.admin.port")
                .orElseThrow(
                    () -> new TigerEnvironmentStartupException("No free port reserved for local Tiger Proxy admin"));
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
        String message =
            "\n" + Banner.toBannerStr("Press " + (textToEnter.isEmpty() ? "" : "'" + textToEnter + "' and ") + "ENTER.",
                RbelAnsiColors.RED_BOLD.toString());
        if (c != null) {
            readCommandFromInput(textToEnter, message, v -> c.readLine());
        } else {
            log.warn("No Console interface found, trying System in stream...");
            BufferedReader rdr = new BufferedReader(new InputStreamReader(System.in));
            readCommandFromInput(textToEnter, message, v -> {
                try {
                    return rdr.readLine();
                } catch (IOException e) {
                    log.warn("Unable to open input stream from console! Continuing with test run...", e);
                    return null;
                }
            });
        }
        log.info("Step wait acknowledged. Continueing...");
    }

    private static void readCommandFromInput(String textToEnter, String message, Function<Void, String> readLine) {
        String cmd = null;
        while (cmd == null || !cmd.equals(textToEnter)) {
            log.info(message);
            if (cmd != null) {
                log.warn("Received: '{}'", cmd);
            }
            cmd = readLine.apply(null);
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TigerTestEnvException("Interrupt received while waiting for console input", e);
            }
        }
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
        addDefaults();
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

    private static void addDefaults() {
        TigerGlobalConfiguration.putValue("tiger.tigerProxy.parsingShouldBlockCommunication",
            "true", SourceType.DEFAULTS);
    }

    private void assertNoCyclesInGraph() {
        servers.values().forEach(server -> cycleChecker(server, new HashSet<>()));
    }

    private void cycleChecker(final AbstractTigerServer currentPosition, final Set<AbstractTigerServer> visitedServer) {
        if (visitedServer.contains(currentPosition)) {
            throw new TigerEnvironmentStartupException(
                "Cyclic graph detected in startup sequence: %s", visitedServer.stream()
                .map(AbstractTigerServer::getServerId)
                .collect(Collectors.toList()));
        }
        if (currentPosition.getDependUponList().isEmpty()) {
            return;
        }
        for (AbstractTigerServer server : currentPosition.getDependUponList()) {
            var newSet = new HashSet<>(visitedServer);
            newSet.add(currentPosition);
            cycleChecker(server, newSet);
        }
    }

    private void createServerObjects() {
        for (Map.Entry<String, CfgServer> serverEntry : configuration.getServers().entrySet()) {
            if (serverEntry.getValue().isActive()) {
                AbstractTigerServer server = createServer(serverEntry.getKey(), serverEntry.getValue());
                servers.put(serverEntry.getKey(), server);
                server.registerNewListener(this);
            }
        }
    }

    public AbstractTigerServer createServer(String serverId, CfgServer config) {
        if (config.getType() == null) {
            throw new TigerTestEnvException("Unable to instantiate server of null type! PLease check your config");
        }
        try {
            String serverType = config.getType().value();
            if (!serverClasses.containsKey(serverType)) {
                throw new TigerTestEnvException(
                    "No server class registered for type " + serverType + " used in server " + serverId);
            }
            return serverClasses.get(serverType)
                .getDeclaredConstructor(TigerTestEnvMgr.class, String.class, CfgServer.class)
                .newInstance(this, serverId, config);
        } catch (RuntimeException | NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            if (e.getCause() != null) {
                if (e.getCause() instanceof TigerConfigurationException) {
                    throw (TigerConfigurationException) e.getCause();
                } else if (e.getCause() instanceof TigerTestEnvException) {
                    throw (TigerTestEnvException) e.getCause();
                }
            }
            throw new TigerTestEnvException(e,
                "Unable to instantiate server of type %s, does it have a constructor(TigerTestenvMgr, String, CfgServer)?",
                config.getType().value());
        }
    }


    @Override
    public void setUpEnvironment() {
        assertNoCyclesInGraph();
        assertNoUnknownServersInDependencies();

        final List<AbstractTigerServer> initialServersToBoot = servers.values().parallelStream()
            .filter(server -> server.getDependUponList().isEmpty())
            .collect(Collectors.toList());

        log.info("Booting following server(s): {}",
            initialServersToBoot.stream()
                .map(AbstractTigerServer::getHostname)
                .collect(Collectors.toList()));

        initialServersToBoot.parallelStream()
            .forEach(this::startServer);

        localTigerProxy.subscribeToTrafficEndpoints(configuration.getTigerProxy());

        log.info(Ansi.colorize("Finished set up test environment OK", RbelAnsiColors.GREEN_BOLD));
    }

    private void assertNoUnknownServersInDependencies() {
        getServers().values().forEach(AbstractTigerServer::getDependUponList);
    }

    private void startServer(AbstractTigerServer server) {
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
    public synchronized void shutDown() {
        log.info(Ansi.colorize("Shutting down all servers...", RbelAnsiColors.RED_BOLD));
        for (AbstractTigerServer server : servers.values()) {
            try {
                server.shutdown();
            } catch (RuntimeException e) {
                log.warn("Exception while shutting down server " + server.getServerId(), e);
            }
        }

        log.info(Ansi.colorize("Shutting down local tiger proxy...", RbelAnsiColors.RED_BOLD));
        if (localTigerProxy != null) {
            localTigerProxy.shutdown();
        }
        if (localTigerProxyApplicationContext != null) {
            localTigerProxyApplicationContext.close();
        }

        log.info(Ansi.colorize("Finished shutdown test environment OK", RbelAnsiColors.RED_BOLD));
    }

    public void receiveTestEnvUpdate(TigerStatusUpdate statusUpdate) {
        listeners.forEach(listener -> listener.receiveTestEnvUpdate(statusUpdate));
    }

    public List<TigerRoute> getRoutes() {
        return servers.values().stream()
            .map(AbstractTigerServer::getRoutes)
            .flatMap(List::stream)
            .collect(Collectors.toUnmodifiableList());
    }

    public Optional<AbstractTigerServer> findServer(String serverName) {
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

    @Override
    public void close() throws Exception {
        shutDown();
    }
}
