/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package de.gematik.test.tiger.testenvmgr;

import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.LOCALPROXY_ADMIN_RESERVED_PORT;
import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.LOCAL_PROXY_ADMIN_PORT;
import static de.gematik.test.tiger.common.config.TigerConfigurationKeys.LOCAL_PROXY_PROXY_PORT;
import static de.gematik.test.tiger.testenvmgr.util.TigerReflectionHelper.createInstanceUnchecked;
import static de.gematik.test.tiger.testenvmgr.util.TigerReflectionHelper.findConstructor;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.rbellogger.util.IRbelMessageListener;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.rbellogger.util.RbelJexlExecutor;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.banner.Banner;
import de.gematik.test.tiger.common.config.ConfigurationValuePrecedence;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.exceptions.GenericTigerException;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.TigerProxyApplication;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
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
import java.text.MessageFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.type.filter.AnnotationTypeFilter;

@Slf4j
@Getter
public class TigerTestEnvMgr
    implements TigerEnvUpdateSender, TigerUpdateListener, DisposableBean, AutoCloseable {

  public static final String HTTP = "http://";
  public static final String HTTPS = "https://";
  public static final String CFG_PROP_NAME_LOCAL_PROXY_ADMIN_PORT = "tiger.tigerProxy.adminPort";
  public static final String CFG_PROP_NAME_LOCAL_PROXY_PROXY_PORT = "tiger.tigerProxy.proxyPort";
  public static final String LOCAL_TIGER_PROXY_TYPE = "local_tiger_proxy";
  private static final String SERVER_PORT = "server.port";
  private static final String TIGER = "tiger";

  static {
    RbelJexlExecutor.initialize();
  }

  private final Configuration configuration;
  private final Map<String, Object> environmentVariables;
  private final List<TigerConfigurationRoute> routesList = new ArrayList<>();
  private final Map<String, AbstractTigerServer> servers = new HashMap<>();

  /**
   * used for longer tasks, unbound in size thus also to be used for server startups and server
   * tasks
   */
  private final ExecutorService cachedExecutor = Executors.newCachedThreadPool();

  /** used for short term tasks like status updates propagation */
  private final ExecutorService fixedPoolExecutor =
      Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

  private final List<TigerUpdateListener> listeners = new ArrayList<>();
  private final List<TigerServerLogListener> logListeners = new ArrayList<>();
  private final DownloadManager downloadManager = new DownloadManager();
  private final Map<String, Class<? extends AbstractTigerServer>> serverClasses = new HashMap<>();
  private final org.slf4j.Logger localProxyLog;
  @Setter public ConfigurableApplicationContext context;
  private TigerProxy localTigerProxy;

  @Getter(AccessLevel.PRIVATE)
  private ServletWebServerApplicationContext localTigerProxyApplicationContext;

  private final AtomicBoolean userAcknowledgedOnWorkflowUi = new AtomicBoolean(false);
  private final AtomicBoolean userConfirmQuit = new AtomicBoolean(false);
  private boolean shouldAbortTestExecution = false;
  @Getter private boolean userPressedFailTestExecution = false;
  private boolean isShuttingDown = false;
  private boolean isShutDown = false;
  @Setter private boolean workflowUiSentFetch = false;

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

  public static Map<String, Object> getConfiguredLoggingLevels() {
    return TigerGlobalConfiguration.readMapWithCaseSensitiveKeys(TIGER, "logging", "level")
        .entrySet()
        .stream()
        .collect(Collectors.toMap(entry -> "logging.level." + entry.getKey(), Entry::getValue));
  }

  public static Map<String, String> getTigerLibConfiguration() {
    return TigerGlobalConfiguration.readMapWithCaseSensitiveKeys(TIGER, "lib").entrySet().stream()
        .collect(Collectors.toMap(entry -> "tiger.lib." + entry.getKey(), Entry::getValue));
  }

  @SuppressWarnings("unused")
  private static void readCommandFromInput(
      String textToEnter, String message, Function<Void, String> readLine) {
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

  private static Configuration readConfiguration() {
    TigerGlobalConfiguration.initialize();
    addDefaults();
    return TigerGlobalConfiguration.instantiateConfigurationBean(Configuration.class, TIGER)
        .orElseGet(Configuration::new);
  }

  private static void addDefaults() {
    TigerGlobalConfiguration.putValue(
        "tiger.tigerProxy.parsingShouldBlockCommunication",
        "true",
        ConfigurationValuePrecedence.DEFAULTS);
  }

  public void startLocalTigerProxyIfActivated() {
    if (configuration.isLocalProxyActive()) {
      try {
        TigerServerLogManager.addProxyCustomerAppender(this, localProxyLog);
      } catch (NoClassDefFoundError error) {
        log.warn(
            "Unable to detect logback library! Log appender for local proxy status messages not"
                + " activated");
      }

      localTigerProxy = startLocalTigerProxy(configuration);
      proxyStatusMessage(
          "Local Tiger Proxy URL http://localhost:" + localTigerProxy.getProxyPort());
      proxyStatusMessage(
          "Local Tiger Proxy UI http://localhost:"
              + localTigerProxyApplicationContext.getWebServer().getPort()
              + "/");
      environmentVariables.put("PROXYHOST", "host.docker.internal");
      environmentVariables.put("PROXYPORT", localTigerProxy.getProxyPort());
      try {
        TigerServerLogManager.addProxyCustomerAppender(this, localTigerProxy.getLog());
      } catch (NoClassDefFoundError error) {
        log.warn(
            "Unable to detect logback library! Log appender feature for local Tiger Proxy not"
                + " activated");
      }
      proxyStatusMessage("Local Tiger Proxy started");

    } else {
      log.info(Ansi.colorize("Local Tiger Proxy deactivated", RbelAnsiColors.RED_BOLD));
      localTigerProxy = null;
    }
  }

  private void proxyStatusMessage(String statusMessage) {
    publishNewStatusUpdate(
        TigerServerStatusUpdate.builder()
            .type(LOCAL_TIGER_PROXY_TYPE)
            .status(TigerServerStatus.RUNNING)
            .statusMessage(statusMessage)
            .baseUrl(
                "http://localhost:"
                    + localTigerProxyApplicationContext.getWebServer().getPort()
                    + "/")
            .build());
    if (localProxyLog.isInfoEnabled()) {
      localProxyLog.info(Ansi.colorize(statusMessage, RbelAnsiColors.BLUE_BOLD));
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
            "Tiger configuration: "
                + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(configuration));
      } catch (JsonProcessingException e) {
        log.error("Unable to dump tiger configuration in " + getClass().getSimpleName(), e);
      }
      try {
        log.debug(
            "Environment variables: "
                + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(System.getenv()));
      } catch (JsonProcessingException e) {
        log.error("Unable to dump os env variables in " + getClass().getSimpleName(), e);
      }
    }
  }

  private void lookupServerPluginsInClasspath() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(TigerServerType.class));
    Set<BeanDefinition> serverBeanDefinitions =
        scanner.findCandidateComponents("de.gematik.test.tiger");

    for (BeanDefinition serverBeanDefinition : serverBeanDefinitions) {
      try {
        Class<?> clz = Class.forName(serverBeanDefinition.getBeanClassName());
        String typeToken = clz.getAnnotation(TigerServerType.class).value();
        serverClasses.put(typeToken, clz.asSubclass(AbstractTigerServer.class));
        log.info(
            "Registered server type {} with class {}",
            typeToken,
            serverBeanDefinition.getBeanClassName());
      } catch (ClassNotFoundException e) {
        throw new TigerTestEnvException(
            "Unable to instantiate / find class "
                + serverBeanDefinition.getBeanClassName()
                + " for server",
            e);
      }
    }
  }

  private TigerProxy startLocalTigerProxy(Configuration configuration) {
    log.info(
        "\n" + Banner.toBannerStr("STARTING LOCAL PROXY...", RbelAnsiColors.BLUE_BOLD.toString()));

    final TigerProxy proxy;
    if (configuration.getTigerProxy() == null) {
      configuration.setTigerProxy(TigerProxyConfiguration.builder().build());
    }
    TigerProxyConfiguration proxyConfig = configuration.getTigerProxy();
    if (StringUtils.isEmpty(proxyConfig.getName())) {
      proxyConfig.setName(LOCAL_TIGER_PROXY_TYPE);
    }
    proxyConfig.setSkipTrafficEndpointsSubscription(true);
    proxyConfig.setStandalone(false);
    if (proxyConfig.getProxyRoutes() == null) {
      proxyConfig.setProxyRoutes(List.of());
    }

    Map<String, Object> properties = new HashMap<>();
    if (configuration.getTigerProxy().getAdminPort() == 0) {
      int port =
          LOCALPROXY_ADMIN_RESERVED_PORT
              .getValue()
              .orElseThrow(
                  () ->
                      new TigerEnvironmentStartupException(
                          "No free port reserved for local Tiger Proxy admin"));
      properties.put(SERVER_PORT, Integer.toString(port));
    } else {
      properties.put(SERVER_PORT, Integer.toString(configuration.getTigerProxy().getAdminPort()));
      LOCALPROXY_ADMIN_RESERVED_PORT.putValue(configuration.getTigerProxy().getAdminPort());
    }
    properties.putAll(getConfiguredLoggingLevels());
    properties.put("spring.mustache.enabled", false); // TGR-875 avoid warning in console
    properties.put("spring.mustache.check-template-location", false);
    log.info("Starting with port {}", properties.get(SERVER_PORT));

    localTigerProxyApplicationContext =
        (ServletWebServerApplicationContext)
            new SpringApplicationBuilder()
                .bannerMode(Mode.OFF)
                .sources(TigerProxyApplication.class)
                .web(WebApplicationType.SERVLET)
                .registerShutdownHook(false)
                .initializers(
                    ac ->
                        ((GenericApplicationContext) ac)
                            .registerBean(
                                "proxyConfig",
                                TigerProxyConfiguration.class,
                                () -> proxyConfig,
                                bd -> bd.setPrimary(true)))
                .properties(properties)
                .run();

    proxy = localTigerProxyApplicationContext.getBean(TigerProxy.class);

    LOCAL_PROXY_PROXY_PORT.putValue(proxy.getProxyPort());
    LOCAL_PROXY_ADMIN_PORT.putValue(proxy.getAdminPort());

    return proxy;
  }

  public void publishNewStatusUpdate(TigerServerStatusUpdate update) {
    publishStatusUpdateToListeners(
        TigerStatusUpdate.builder()
            .serverUpdate(
                new LinkedHashMap<>(
                    Map.of(
                        getLocalTigerProxyOptional()
                            .flatMap(TigerProxy::getName)
                            .orElse(getLocalTigerProxyOrFail().proxyName()),
                        update)))
            .build(),
        listeners);
  }

  public synchronized void publishStatusUpdateToListeners(
      TigerStatusUpdate update, List<TigerUpdateListener> listeners) {
    if (!isShuttingDown) {
      getFixedPoolExecutor()
          .submit(() -> listeners.forEach(listener -> listener.receiveTestEnvUpdate(update)));
    }
  }

  private void assertNoCyclesInGraph() {
    servers.values().forEach(server -> cycleChecker(server, new HashSet<>()));
  }

  private void cycleChecker(
      final AbstractTigerServer currentPosition, final Set<AbstractTigerServer> visitedServer) {
    if (visitedServer.contains(currentPosition)) {
      throw new TigerEnvironmentStartupException(
          "Cyclic graph detected in startup sequence: %s",
          visitedServer.stream().map(AbstractTigerServer::getServerId).toList());
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
      throw new TigerTestEnvException(
          "Unable to instantiate server of null type! Please check your config");
    }
    try {
      String serverTypeName = config.getType();
      if (!serverClasses.containsKey(serverTypeName)) {
        throw new TigerTestEnvException(
            MessageFormat.format(
                "No server class registered for type {0} used in server {1}. "
                    + "Did you add appropriate dependencies for {0}?",
                serverTypeName, serverId));
      }
      val serverClass = serverClasses.get(serverTypeName);
      return findConstructor(serverClass, String.class, CfgServer.class, TigerTestEnvMgr.class)
          .map(c -> createInstanceUnchecked(c, serverId, config, this))
          .or(
              () ->
                  findConstructor(serverClass, TigerTestEnvMgr.class, String.class, CfgServer.class)
                      .map(
                          constructor -> {
                            log.warn(
                                "Using DEPRECATED constructor for server {}! "
                                    + "Using (TigerTestenvMg, String, CfgServer), "
                                    + "plase change constructor to match param order:"
                                    + " (String, CfgServer, TigerTestenvMgr)!",
                                serverId);
                            return createInstanceUnchecked(constructor, this, serverId, config);
                          }))
          .orElseThrow(
              () ->
                  new TigerTestEnvException(
                      "No suitable constructor found for server. Expected constructor"
                          + "(String serverName, CfgServer config, TigerTestenvMgr envMgr)"));
    } catch (RuntimeException e) {
      throw handleExceptionMinimizingStackTrace(config, e);
    }
  }

  private static RuntimeException handleExceptionMinimizingStackTrace(
      CfgServer config, Exception e) {
    if (e instanceof TigerTestEnvException tte) {
      return tte;
    }
    if (e.getCause() != null) {
      if (e.getCause() instanceof TigerConfigurationException tce) {
        return (TigerConfigurationException) (tce.getCause() == null ? tce : tce.getCause());
      } else if (e.getCause() instanceof TigerTestEnvException tee) {
        return (TigerTestEnvException) (tee.getCause() == null ? tee : tee.getCause());
      }
    }
    return new TigerTestEnvException(
        e,
        "Unable to instantiate server of type %s, does it have a constructor"
            + "(String serverName, CfgServer config, TigerTestenvMgr envMgr)?",
        config.getType());
  }

  public void setUpEnvironment() {
    setUpEnvironment(Optional.empty());
  }

  public void setUpEnvironment(Optional<IRbelMessageListener> localTigerProxyMessageListener) {
    try {
      assertNoCyclesInGraph();
      assertNoUnknownServersInDependencies();

      startLocalTigerProxyIfActivated();
      localTigerProxyMessageListener.ifPresent(
          provider ->
              getLocalTigerProxyOptional()
                  .ifPresent(proxy -> proxy.addRbelMessageListener(provider)));

      Map<String, TigerServerStatusUpdate> activeServers =
          servers.values().stream()
              .filter(server -> server.getConfiguration().isActive())
              .collect(
                  Collectors.toMap(
                      AbstractTigerServer::getServerId,
                      server ->
                          TigerServerStatusUpdate.builder()
                              .type(server.getConfiguration().getType())
                              .status(TigerServerStatus.NEW)
                              .build()));

      getFixedPoolExecutor()
          .submit(
              () ->
                  listeners.parallelStream()
                      .forEach(
                          listener ->
                              listener.receiveTestEnvUpdate(
                                  TigerStatusUpdate.builder()
                                      .serverUpdate(new LinkedHashMap<>(activeServers))
                                      .build())));

      final List<AbstractTigerServer> initialServersToBoot =
          servers.values().parallelStream()
              .filter(server -> server.getDependUponList().isEmpty())
              .toList();

      log.info(
          "Booting following server(s): {}",
          initialServersToBoot.stream().map(AbstractTigerServer::getHostname).toList());

      initialServersToBoot.parallelStream().forEach(this::startServer);

      if (isLocalTigerProxyActive()) {
        log.info("Subscribing to traffic endpoints with local tiger proxy...");
        localTigerProxy.subscribeToTrafficEndpoints();
      }

      ensureLocalTigerProxyReadFilesSuccesfully();

      log.info(Ansi.colorize("Finished set up test environment OK", RbelAnsiColors.GREEN_BOLD));
    } catch (GenericTigerException rte) {
      shutDown();
      throw rte;
    }
  }

  private void ensureLocalTigerProxyReadFilesSuccesfully() {
    getLocalTigerProxyOptional().ifPresent(TigerProxy::waitForAllCurrentMessagesToBeParsed);
  }

  public void setDefaultProxyToLocalTigerProxy() {
    String httpProxyHost = "http.proxyHost";
    String httpsProxyHost = "https.proxyHost";
    if (System.getProperty(httpProxyHost) != null || System.getProperty(httpsProxyHost) != null) {
      log.info(
          Ansi.colorize(
              "SKIPPING TIGER PROXY settings as System Property is set already...",
              RbelAnsiColors.RED_BOLD));
    } else {
      getLocalTigerProxyOptional()
          .ifPresent(
              proxy -> {
                log.info(
                    Ansi.colorize(
                        "SETTING TIGER PROXY http://localhost:" + proxy.getProxyPort() + "...",
                        RbelAnsiColors.BLUE_BOLD));
                System.setProperty(httpProxyHost, "localhost");
                System.setProperty("http.proxyPort", String.valueOf(proxy.getProxyPort()));
                System.setProperty("http.nonProxyHosts", "localhost|127.0.0.1");
                System.setProperty(httpsProxyHost, "localhost");
                System.setProperty("https.proxyPort", String.valueOf(proxy.getProxyPort()));
                System.setProperty("java.net.useSystemProxies", "true");
              });
    }
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
      synchronized (server) { // NOSONAR
        // we REALLY want to synchronize ONLY on the server!
        if (server.getStatus() != TigerServerStatus.NEW) {
          return;
        }
        server.start(this);
      }

      cachedExecutor
          .submit(
              () ->
                  servers.values().parallelStream()
                      .filter(
                          candidate -> {
                            log.debug(
                                "Considering to start server {} with status {}...",
                                candidate.getServerId(),
                                candidate.getStatus());
                            return candidate.getStatus() == TigerServerStatus.NEW;
                          })
                      .filter(
                          candidate ->
                              candidate.getDependUponList().stream()
                                  .filter(
                                      depending ->
                                          depending.getStatus() != TigerServerStatus.RUNNING)
                                  .findAny()
                                  .isEmpty())
                      .forEach(
                          toBeStartedServer -> {
                            log.info(
                                "Starting server {} with status {}",
                                toBeStartedServer.getServerId(),
                                toBeStartedServer.getStatus());
                            startServer(toBeStartedServer);
                          }))
          .get();
    } catch (RuntimeException | ExecutionException e) {
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
    log.info(Ansi.colorize("Shutting down all servers...", RbelAnsiColors.RED_BOLD));
    servers.values().stream()
        .forEach(
            server -> {
              try {
                server.stopServerAndCleanUp();
              } catch (RuntimeException e) {
                log.warn("Exception while shutting down server " + server.getServerId(), e);
              }
            });

    log.info(Ansi.colorize("Sending shutdown to executor pool...", RbelAnsiColors.RED_BOLD));
    cachedExecutor.shutdownNow();
    fixedPoolExecutor.shutdownNow();

    if (localTigerProxy != null) {
      log.info(Ansi.colorize("Shutting down local tiger proxy...", RbelAnsiColors.RED_BOLD));
      localTigerProxy.close();
    }
    if (localTigerProxyApplicationContext != null) {
      localTigerProxyApplicationContext.close();
      publishNewStatusUpdate(
          TigerServerStatusUpdate.builder()
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

  @SuppressWarnings("unused")
  public List<TigerProxyRoute> getRoutes() {
    return servers.values().stream()
        .map(AbstractTigerServer::getServerRoutes)
        .flatMap(List::stream)
        .toList();
  }

  public Optional<AbstractTigerServer> findServer(String serverName) {
    return Optional.ofNullable(servers.get(serverName));
  }

  /**
   * @return local Tiger Proxy instance
   * @deprecated to avoid the null pointer hassle, the API has been changed to return Optional, see
   *     {@link #getLocalTigerProxyOrFail()} and {@link #getLocalTigerProxyOptional()}.
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

  @SuppressWarnings("unused")
  public ExecutorService getExecutor() {
    return cachedExecutor;
  }

  @Override
  public void registerNewListener(TigerUpdateListener listener) {
    listeners.add(listener);
  }

  @Override
  public void registerLogListener(TigerServerLogListener listener) {
    logListeners.add(listener);
  }

  public void receivedConfirmationFromWorkflowUi(boolean executionShouldFail) {
    userPressedFailTestExecution = executionShouldFail;
    userAcknowledgedOnWorkflowUi.set(true);
  }

  public void receivedQuitConfirmationFromWorkflowUi() {
    userPressedFailTestExecution = false;
    userConfirmQuit.set(true);
  }

  @Override
  public void destroy() throws Exception {
    shutDown();
  }

  @Override
  public void close() throws Exception {
    shutDown();
  }

  public void abortTestExecution() {
    shouldAbortTestExecution = true;
  }
}
