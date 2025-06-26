/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.testenvmgr.servers;

import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.config.ConfigurationValuePrecedence;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.env.TigerEnvUpdateSender;
import de.gematik.test.tiger.testenvmgr.env.TigerServerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.env.TigerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.env.TigerUpdateListener;
import de.gematik.test.tiger.testenvmgr.servers.log.TigerServerLogManager;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;

@Getter
public abstract class AbstractTigerServer implements TigerEnvUpdateSender {

  public static final int DEFAULT_STARTUP_TIMEOUT_IN_SECONDS = 20;
  private static final String LETTTER_DIGIT_REGEX = "[a-zA-Z0-9]";
  private static final String LETTER_DIGIT_HYPHEN_REGEX = "[a-zA-Z0-9\\-]";
  private static final String SERVER_NAME_REGEX =
      "^"
          + LETTTER_DIGIT_REGEX
          + "+("
          + LETTER_DIGIT_HYPHEN_REGEX
          + "+"
          + LETTTER_DIGIT_REGEX
          + ")*$";
  // protected because implementing servers use this var
  protected final org.slf4j.Logger log;
  private String hostname;
  private final String serverId;
  private final List<String> environmentProperties = new ArrayList<>();
  private final List<TigerProxyRoute> serverRoutes = new ArrayList<>();
  private final TigerTestEnvMgr tigerTestEnvMgr;
  private final List<TigerUpdateListener> listeners = new ArrayList<>();
  private final List<TigerServerLogListener> logListeners = new ArrayList<>();
  private CfgServer configuration;
  private TigerServerStatus status = TigerServerStatus.NEW;

  protected AbstractTigerServer(
      String serverId, CfgServer configuration, TigerTestEnvMgr tigerTestEnvMgr) {
    this.serverId = serverId;
    this.tigerTestEnvMgr = tigerTestEnvMgr;
    this.configuration = configuration;
    log = org.slf4j.LoggerFactory.getLogger("TgrSrv-" + serverId);
    try {
      TigerServerLogManager.addAppenders(this);
    } catch (NoClassDefFoundError ncde) {
      log.warn(
          "Unable to detect logback library! Log appender for server {} not activated", serverId);
    }
    this.hostname = determineHostname();
  }

  private String determineHostname() {
    if (StringUtils.isNotBlank(configuration.getHostname())) {
      return configuration.getHostname();
    } else {
      return serverId;
    }
  }

  @SuppressWarnings("unused")
  public String getServerTypeToken() {
    try {
      return getClass().getAnnotation(TigerServerType.class).value();
    } catch (NullPointerException npe) {
      throw new TigerTestEnvException(
          "Server class "
              + this.getClass()
              + " has no "
              + TigerServerType.class.getCanonicalName()
              + " Annotation!");
    }
  }

  @SuppressWarnings("java:S2139")
  public void start(TigerTestEnvMgr testEnvMgr) {
    if (testEnvMgr.isShuttingDown()) {
      log.debug("Skipping startup, already shutting down...");
      synchronized (this) {
        setStatus(TigerServerStatus.STOPPED, getServerId() + " skipped startup");
      }
      return;
    }
    synchronized (this) {
      if ((getStatus() != TigerServerStatus.NEW) && (getStatus() != TigerServerStatus.STOPPED)) {
        throw new TigerEnvironmentStartupException("Server %s was already started!", getServerId());
      }
    }
    publishNewStatusUpdate(
        TigerServerStatusUpdate.builder()
            .type(getServerTypeToken())
            .status(TigerServerStatus.STARTING)
            .statusMessage("Checking configuration " + getServerId() + "...")
            .build());

    reloadConfiguration();

    try {
      assertThatConfigurationIsCorrect();
    } catch (RuntimeException rte) {
      publishNewStatusUpdate(
          TigerServerStatusUpdate.builder()
              .status(TigerServerStatus.STOPPED)
              .statusMessage("Configuration " + getServerId() + " invalid")
              .build());
      log.error("Invalid configuration for server " + serverId + "! Got " + rte.getMessage(), rte);
      throw rte;
    }

    configuration.getEnvironment().stream()
        .map(testEnvMgr::replaceSysPropsInString)
        .forEach(environmentProperties::add);

    if (configuration.getUrlMappings() != null
        && testEnvMgr.getLocalTigerProxyOptional().isPresent()) {
      statusMessage("Adding routes to local tiger proxy for server " + getServerId() + "...");
      serverRoutes.addAll(
          configuration.getUrlMappings().stream()
              .map(
                  mapping -> {
                    if (StringUtils.isBlank(mapping)
                        || !mapping.contains("-->")
                        || mapping.split(" --> ", 2).length != 2) {
                      throw new TigerConfigurationException(
                          "The urlMappings configuration '"
                              + mapping
                              + "' is not correct. Please check your .yaml-file.");
                    }

                    String[] routeParts = mapping.split(" --> ", 2);
                    return testEnvMgr
                        .getLocalTigerProxyOptional()
                        .map(
                            proxy ->
                                proxy.addRoute(
                                    TigerConfigurationRoute.builder()
                                        .from(routeParts[0])
                                        .to(routeParts[1])
                                        .build()));
                  })
              .map(Optional::orElseThrow)
              .toList());
    }

    try {
      if (testEnvMgr.isShuttingDown()) {
        log.debug("Skipping startup, already shutting down...");
        synchronized (this) {
          setStatus(TigerServerStatus.STOPPED, getServerId() + " skipped startup");
        }
        return;
      }
      performStartup();
    } catch (Throwable t) {
      log.warn(
          String.format(
              t.getClass().getSimpleName()
                  + " during startup of server %s. Used configuration was %s",
              getServerId(),
              TigerSerializationUtil.toJson(getConfiguration())),
          t);
      throw t;
    }
    statusMessage(getServerId() + " started");

    processExports();

    synchronized (this) {
      setStatus(TigerServerStatus.RUNNING, getServerId() + " READY");
    }
  }

  private void reloadConfiguration() {
    try {
      this.configuration =
          TigerGlobalConfiguration.instantiateConfigurationBean(
                  getConfigurationBeanClass(), "tiger", "servers", getServerId())
              .orElseThrow(
                  () ->
                      new TigerEnvironmentStartupException(
                          "Could not reload configuration for server with id %s", getServerId()));
      tigerTestEnvMgr.getConfiguration().getServers().put(getServerId(), configuration);
      hostname = determineHostname();
    } catch (TigerConfigurationException e) {
      throw new TigerEnvironmentStartupException(
          "Could not reload configuration for server " + getServerId(), e);
    }
  }

  public Class<? extends CfgServer> getConfigurationBeanClass() {
    return CfgServer.class;
  }

  public abstract void performStartup();

  protected void processExports() {
    configuration
        .getExports()
        .forEach(
            exp -> {
              String[] kvp = exp.split("=", 2);
              // ports substitution are only supported for docker based instances
              kvp[1] = kvp[1].replace("${NAME}", getHostname());

              log.info("Setting global property {}={}", kvp[0], kvp[1]);
              TigerGlobalConfiguration.putValue(
                  kvp[0], kvp[1], ConfigurationValuePrecedence.RUNTIME_EXPORT);
            });
  }

  public void assertThatConfigurationIsCorrect() {
    if (StringUtils.isBlank(serverId)) {
      throw new TigerTestEnvException("Server Id must not be blank!");
    }
    assertThatServerNameIsCorrect();
    assertCfgPropertySet(getConfiguration(), "type");
    assertThatHostnameIsUnique();

    // set default values for all types
    if (getConfiguration().getStartupTimeoutSec() == null) {
      log.info("Defaulting startup timeout sec to 20 sec for server {}", serverId);
      getConfiguration().setStartupTimeoutSec(20);
    }
  }

  private void assertThatHostnameIsUnique() {
    tigerTestEnvMgr.getServers().values().stream()
        .filter(other -> other != this)
        .filter(other -> StringUtils.equalsIgnoreCase(other.getHostname(), hostname))
        .findAny()
        .ifPresent(
            other -> {
              throw new TigerConfigurationException(
                  "Non-unique hostname detected: '"
                      + other.getHostname()
                      + "' of server '"
                      + other.getServerId()
                      + "' is (case-insensitive) equal to '"
                      + hostname
                      + "' of server '"
                      + getServerId()
                      + "'");
            });
  }

  private void assertThatServerNameIsCorrect() {
    if (!getHostname().matches(SERVER_NAME_REGEX)) {
      throw new TigerConfigurationException(
          "Hostname '" + getHostname() + "' not valid (used for server '" + getServerId() + "')");
    }
  }

  @SneakyThrows
  protected void assertCfgPropertySet(Object target, String... propertyNames) {
    for (String propertyName : propertyNames) {
      Method mthd =
          target
              .getClass()
              .getMethod(
                  "get"
                      + Character.toUpperCase(propertyName.charAt(0))
                      + propertyName.substring(1));
      target = mthd.invoke(target);
      if (target == null) {
        throw new TigerTestEnvException(
            "Server '%s' must have property %s be set and not be NULL!",
            getServerId(), propertyName);
      }
      if (target instanceof List) {
        assertListCfgPropertySet((List<?>) target, propertyName);
      } else if (target instanceof String asString && asString.isBlank()) {
        throw new TigerTestEnvException(
            "Server %s must have property %s be set and not be empty!",
            getServerId(), propertyName);
      }
    }
  }

  private void assertListCfgPropertySet(List<?> target, String propertyName) {
    if (target.isEmpty() || target.get(0) == null) {
      throw new TigerTestEnvException(
          "Server %s must have property %s be set and must contain at least one non empty entry",
          getServerId(), propertyName);
    }
    if (target.get(0) instanceof String && ((String) target.get(0)).isBlank()) {
      throw new TigerTestEnvException(
          "Server %s must have property %s be set and contain at least one non empty entry!",
          getServerId(), propertyName);
    }
  }

  public Optional<Integer> getStartupTimeoutSec() {
    return Optional.ofNullable(configuration.getStartupTimeoutSec());
  }

  public void addServerToLocalProxyRouteMap(URL url) {
    addRoute(
        TigerConfigurationRoute.builder()
            .from(TigerTestEnvMgr.HTTP + getHostname())
            .to(extractBaseUrl(url))
            .build());
  }

  String extractBaseUrl(URL url) {
    try {
      int port = url.getPort();
      if (port == -1) {
        port = url.getDefaultPort();
      }
      return url.toURI().getScheme() + "://" + url.getHost() + ":" + port;
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Error while convert to URI: '" + url + "'", e);
    }
  }

  void addRoute(TigerConfigurationRoute newRoute) {
    getTigerTestEnvMgr()
        .getLocalTigerProxyOptional()
        .ifPresent(proxy -> serverRoutes.add(proxy.addRoute(newRoute)));
  }

  void removeAllRoutesForServer() {
    getTigerTestEnvMgr()
        .getLocalTigerProxyOptional()
        .ifPresent(
            proxy -> {
              log.info("Removing routes for {}...", getServerId());
              serverRoutes.stream().map(TigerProxyRoute::getId).forEach(proxy::removeRoute);
            });
  }

  public void stopServerAndCleanUp() {
    shutdown();
    removeAllRoutesForServer();
  }

  public abstract void shutdown();

  public List<AbstractTigerServer> getDependUponList() {
    if (StringUtils.isBlank(getConfiguration().getDependsUpon())) {
      return List.of();
    }
    return Stream.of(getConfiguration().getDependsUpon().split(","))
        .filter(StringUtils::isNotBlank)
        .map(String::trim)
        .map(
            serverName ->
                tigerTestEnvMgr
                    .findServer(serverName)
                    .orElseThrow(
                        () ->
                            new TigerEnvironmentStartupException(
                                "Unknown server: '%s' in dependUponList of server '%s'",
                                serverName, getServerId())))
        .toList();
  }

  public String getDestinationUrl(String fallbackProtocol) {
    throw new TigerTestEnvException(
        "Sophisticated reverse proxy for '%s' is not supported!", getClass().getSimpleName());
  }

  public void setStatus(TigerServerStatus newStatus) {
    setStatus(newStatus, null);
  }

  public void setStatus(TigerServerStatus newStatus, String statusMessage) {
    this.status = newStatus;
    publishNewStatusUpdate(
        TigerServerStatusUpdate.builder().status(newStatus).statusMessage(statusMessage).build());
    if (statusMessage != null && log.isInfoEnabled()) {
      if (newStatus == TigerServerStatus.STOPPED) {
        log.info(Ansi.colorize(statusMessage, RbelAnsiColors.RED_BOLD));
      } else {
        log.info(Ansi.colorize(statusMessage, RbelAnsiColors.GREEN_BOLD));
      }
    }
  }

  public void registerNewListener(TigerUpdateListener listener) {
    this.listeners.add(listener);
  }

  public void registerLogListener(TigerServerLogListener listener) {
    this.logListeners.add(listener);
  }

  public void statusMessage(String statusMessage) {
    publishNewStatusUpdate(TigerServerStatusUpdate.builder().statusMessage(statusMessage).build());
    if (log.isInfoEnabled()) {
      log.info(Ansi.colorize(statusMessage, RbelAnsiColors.GREEN_BOLD));
    }
  }

  public void publishNewStatusUpdate(TigerServerStatusUpdate update) {
    update.setType(getServerTypeToken());
    tigerTestEnvMgr.publishStatusUpdateToListeners(
        TigerStatusUpdate.builder()
            .serverUpdate(new LinkedHashMap<>(Map.of(serverId, update)))
            .build(),
        listeners);
  }
}
