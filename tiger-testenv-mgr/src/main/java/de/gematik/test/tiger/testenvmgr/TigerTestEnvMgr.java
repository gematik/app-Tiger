/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.LOCALPROXY_ADMIN_RESERVED_PORT;
import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.LOCAL_PROXY_ADMIN_PORT;
import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.LOCAL_PROXY_PROXY_PORT;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.banner.Banner;
import de.gematik.test.tiger.common.config.SourceType;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import de.gematik.test.tiger.proxy.IRbelMessageListener;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.TigerProxyApplication;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import de.gematik.test.tiger.testenvmgr.env.*;
import de.gematik.test.tiger.testenvmgr.servers.AbstractTigerServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerLogListener;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerStatus;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerType;
import de.gematik.test.tiger.testenvmgr.servers.log.TigerServerLogManager;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
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
public class TigerTestEnvMgr implements TigerEnvUpdateSender, TigerUpdateListener, DisposableBean, AutoCloseable {

    public static final String HTTP = "http://";
    public static final String HTTPS = "https://";
    private final Configuration configuration;
    private final Map<String, Object> environmentVariables;
    private TigerProxy localTigerProxy;
    public static final String CFG_PROP_NAME_LOCAL_PROXY_ADMIN_PORT = "tiger.tigerProxy.adminPort";
    public static final String CFG_PROP_NAME_LOCAL_PROXY_PROXY_PORT = "tiger.tigerProxy.proxyPort";
    public static final String LOCAL_TIGER_PROXY_TYPE = "local_tiger_proxy";
    private final List<TigerRoute> routesList = new ArrayList<>();
    private final Map<String, AbstractTigerServer> servers = new HashMap<>();
    private final ExecutorService executor = Executors
        .newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    private final List<TigerUpdateListener> listeners = new ArrayList<>();

    private final List<TigerServerLogListener> logListeners = new ArrayList<>();

    private final DownloadManager downloadManager = new DownloadManager();
    private ServletWebServerApplicationContext localTigerProxyApplicationContext;

    private boolean userAcknowledgedShutdown = false;
    private boolean userAcknowledgedContinueTestRun = false;
    private boolean userAcknowledgedFailingTestRun = false;
    private boolean isShuttingDown = false;

    @Getter
    private boolean isShutDown = false;

    @Setter
    @Getter
    private boolean workflowUiSentFetch = false;
    private final Map<String, Class<? extends AbstractTigerServer>> serverClasses = new HashMap<>();

    private final org.slf4j.Logger localProxyLog;

    public TigerTestEnvMgr() {
        this.configuration = readConfiguration();
        this.environmentVariables = new HashMap<>();
        localProxyLog = org.slf4j.LoggerFactory.getLogger("localTigerProxy");

        logConfiguration();

        lookupServerPluginsInClasspath();

        try {
            createServerObjects();

            log.info("Tiger Testenv mgr created OK");
        } catch (RuntimeException e) {
            shutDown();
            throw e;
        }
    }

    public void startLocalTigerProxyIfActivated() {
        if (configuration.isLocalProxyActive()) {
            TigerServerLogManager.addProxyCustomerAppender(this, localProxyLog);
            localTigerProxy = startLocalTigerProxy(configuration);
            proxyStatusMessage("Local Tiger Proxy URL http://localhost:" +
                localTigerProxy.getProxyPort(), RbelAnsiColors.BLUE_BOLD);
            proxyStatusMessage("Local Tiger Proxy UI http://localhost:" +
                localTigerProxyApplicationContext.getWebServer().getPort() + "/webui", RbelAnsiColors.BLUE_BOLD);
            environmentVariables.put("PROXYHOST", "host.docker.internal");
            environmentVariables.put("PROXYPORT", localTigerProxy.getProxyPort());
            TigerServerLogManager.addProxyCustomerAppender(this, localTigerProxy.getLog());
        } else {
            log.info(Ansi.colorize("Local Tiger Proxy deactivated", RbelAnsiColors.RED_BOLD));
            localTigerProxy = null;
        }
    }

    private void proxyStatusMessage(String statusMessage, RbelAnsiColors color) {
        publishNewStatusUpdate(TigerServerStatusUpdate.builder()
            .type(LOCAL_TIGER_PROXY_TYPE)
            .status(TigerServerStatus.RUNNING)
            .statusMessage(statusMessage)
            .baseUrl("http://localhost:" + localTigerProxyApplicationContext.getWebServer().getPort() + "/webui")
            .build());
        if (localProxyLog.isInfoEnabled()) {
            localProxyLog.info(Ansi.colorize(statusMessage, color));
        }
    }

    private void logConfiguration() {
        if (log.isDebugEnabled()) {
            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(Include.NON_NULL);
            mapper.setSerializationInclusion(Include.NON_EMPTY);
            mapper.setSerializationInclusion(Include.NON_DEFAULT);
            try {
                log.debug(
                    "Tiger configuration: " + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(configuration));
            } catch (JsonProcessingException e) {
                log.error("Unable to dump tiger configuration in " + getClass().getSimpleName(), e);
            }
            try {
                log.debug("Environment variables: " + mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(System.getenv()));
            } catch (JsonProcessingException e) {
                log.error("Unable to dump os env variables in " + getClass().getSimpleName(), e);
            }
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
        proxyConfig.setName(LOCAL_TIGER_PROXY_TYPE);
        proxyConfig.setSkipTrafficEndpointsSubscription(true);
        if (proxyConfig.getProxyRoutes() == null) {
            proxyConfig.setProxyRoutes(List.of());
        }

        Map<String, Object> properties = new HashMap<>(TigerSerializationUtil.toMap(proxyConfig, "tigerProxy"));
        if (configuration.getTigerProxy().getAdminPort() == 0) {
            int port = LOCALPROXY_ADMIN_RESERVED_PORT.getValue()
                .orElseThrow(
                    () -> new TigerEnvironmentStartupException("No free port reserved for local Tiger Proxy admin"));
            properties.put("server.port", Integer.toString(port));
        } else {
            properties.put("server.port", Integer.toString(configuration.getTigerProxy().getAdminPort()));
            LOCALPROXY_ADMIN_RESERVED_PORT.putValue(configuration.getTigerProxy().getAdminPort());
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

        LOCAL_PROXY_PROXY_PORT.putValue(localTigerProxy.getProxyPort());
        LOCAL_PROXY_ADMIN_PORT.putValue(localTigerProxy.getAdminPort());

        return localTigerProxy;
    }

    public void publishNewStatusUpdate(TigerServerStatusUpdate update) {
        publishStatusUpdateToListeners(TigerStatusUpdate.builder()
                        .serverUpdate(new LinkedHashMap<>(Map.of(getLocalTigerProxyOptional()
                            .flatMap(TigerProxy::getName)
                            .orElse(getLocalTigerProxy().proxyName()), update)))
                        .build(), listeners);
    }

    public synchronized void publishStatusUpdateToListeners(TigerStatusUpdate update, List<TigerUpdateListener> listeners) {
        if (getExecutor() != null && !isShuttingDown) {
            getExecutor().submit(
                () -> listeners.stream()
                    .forEach(listener -> listener.receiveTestEnvUpdate(update))
            );
        }
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
            throw new TigerTestEnvException("Unable to instantiate server of null type! Please check your config");
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

    public void setUpEnvironment() {
        setUpEnvironment(Optional.empty());
    }

    public void setUpEnvironment(Optional<IRbelMessageListener> localTigerProxyMessageListener) {
        assertNoCyclesInGraph();
        assertNoUnknownServersInDependencies();

        startLocalTigerProxyIfActivated();
        localTigerProxyMessageListener.ifPresent(provider -> getLocalTigerProxyOptional()
            .ifPresent(proxy -> proxy.addRbelMessageListener(provider)));

        Map<String, TigerServerStatusUpdate> activeServers = servers.values().stream()
            .filter(server -> server.getConfiguration().isActive())
            .collect(Collectors.toMap(AbstractTigerServer::getServerId, server -> TigerServerStatusUpdate.builder()
                .type(server.getConfiguration().getType().value())
                .status(TigerServerStatus.NEW)
                .build()));

        getExecutor().submit(
            () -> listeners.parallelStream()
                .forEach(listener -> listener.receiveTestEnvUpdate(TigerStatusUpdate.builder()
                    .serverUpdate(new LinkedHashMap<>(activeServers))
                    .build()))
        );

        final List<AbstractTigerServer> initialServersToBoot = servers.values().parallelStream()
            .filter(server -> server.getDependUponList().isEmpty())
            .collect(Collectors.toList());

        log.info("Booting following server(s): {}",
            initialServersToBoot.stream()
                .map(AbstractTigerServer::getHostname)
                .collect(Collectors.toList()));

        initialServersToBoot.parallelStream()
            .forEach(this::startServer);

        if (isLocalTigerProxyActive()) {
            localTigerProxy.subscribeToTrafficEndpoints(configuration.getTigerProxy());
        }

        log.info(Ansi.colorize("Finished set up test environment OK", RbelAnsiColors.GREEN_BOLD));
    }

    private void assertNoUnknownServersInDependencies() {
        getServers().values().forEach(AbstractTigerServer::getDependUponList);
    }

    private void startServer(AbstractTigerServer server) {
        try {
            if (isShuttingDown) {
                log.warn("Aborting startup of {}, already shutting down!", server.getServerId());
                return;
            }
            synchronized (server) { //NOSONAR
                // we REALLY want to synchronize ONLY on the server!
                if (server.getStatus() != TigerServerStatus.NEW) {
                    return;
                }
                server.start(this);
            }

            executor.submit(() ->
                servers.values().parallelStream()
                    .peek(toBeStartedServer -> log.debug("Considering to start server {} with status {}...",
                        toBeStartedServer.getServerId(), toBeStartedServer.getStatus()))
                    .filter(candidate -> candidate.getStatus() == TigerServerStatus.NEW)
                    .filter(candidate -> candidate.getDependUponList().stream()
                        .filter(depending -> depending.getStatus() != TigerServerStatus.RUNNING)
                        .findAny().isEmpty())
                    .peek(toBeStartedServer -> log.info("Starting server {} with status {}",
                        toBeStartedServer.getServerId(), toBeStartedServer.getStatus()))
                    .forEach(this::startServer))
                .get();
        } catch (RuntimeException e) {
            shutDown();
            throw e;
        } catch (ExecutionException e) {
            throw new TigerEnvironmentStartupException("Error during server startup", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TigerTestEnvException("Interrupt received while starting servers", e);
        }
    }

    public String replaceSysPropsInString(String str) {
        return str;
    }

    public synchronized void shutDown() {
        if (isShuttingDown) {
            return;
        }
        isShuttingDown = true;
        log.info(Ansi.colorize("Sending shutdown to executor pool...", RbelAnsiColors.RED_BOLD));
        executor.shutdownNow();
        log.info(Ansi.colorize("Shutting down all servers...", RbelAnsiColors.RED_BOLD));
        for (AbstractTigerServer server : servers.values()) {
            try {
                server.shutdown();
            } catch (RuntimeException e) {
                log.warn("Exception while shutting down server " + server.getServerId(), e);
            }
        }

        if (localTigerProxy != null) {
            log.info(Ansi.colorize("Shutting down local tiger proxy...", RbelAnsiColors.RED_BOLD));
            localTigerProxy.shutdown();
        }
        if (localTigerProxyApplicationContext != null) {
            localTigerProxyApplicationContext.close();
            publishNewStatusUpdate(TigerServerStatusUpdate.builder()
                .type(LOCAL_TIGER_PROXY_TYPE)
                .status(TigerServerStatus.STOPPED)
                .statusMessage("Local Tiger Proxy stopped")
                .build());
            log.info(Ansi.colorize("Local tiger proxy SHUTDOWN...", RbelAnsiColors.RED_BOLD));
        }

        log.info(Ansi.colorize("Finished shutdown test environment OK", RbelAnsiColors.RED_BOLD));
        isShutDown = true;
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

    /**
     * @return local Tiger Proxy instance
     * @deprecated to avoid the null pointer hassle, the API has been changed to return Optional, see {@link #getLocalTigerProxyOrFail()} and
     * {@link #getLocalTigerProxyOptional()}.
     */
    @Deprecated(since = "1.1.1", forRemoval = true)
    public TigerProxy getLocalTigerProxy() {
        return localTigerProxy;
    }

    public TigerProxy getLocalTigerProxyOrFail() {
        if (localTigerProxy == null) {
            if (isLocalTigerProxyActive()) {
                throw new TigerTestEnvException("Local Tiger Proxy is not activated!");
            } else {
                throw new TigerTestEnvException("Local Tiger Proxy is null!");
            }
        }
        return localTigerProxy;
    }

    public Optional<TigerProxy> getLocalTigerProxyOptional() {
        if (localTigerProxy == null) {
            return Optional.empty();
        }
        return Optional.of(localTigerProxy);
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
        logListeners.add(listener);
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
