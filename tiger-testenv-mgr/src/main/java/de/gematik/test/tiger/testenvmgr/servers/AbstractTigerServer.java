/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.servers;

import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.config.SourceType;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.PkiType;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.pki.KeyMgr;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.env.TigerEnvUpdateSender;
import de.gematik.test.tiger.testenvmgr.env.TigerServerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.env.TigerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.env.TigerUpdateListener;
import de.gematik.test.tiger.testenvmgr.servers.log.TigerServerLogManager;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public abstract class AbstractTigerServer implements TigerEnvUpdateSender {

    public static final int DEFAULT_STARTUP_TIMEOUT_IN_SECONDS = 20;

    private final String hostname;
    private final String serverId;
    private final List<String> environmentProperties = new ArrayList<>();
    private final List<TigerRoute> routes = new ArrayList<>();
    private final TigerTestEnvMgr tigerTestEnvMgr;
    private final List<TigerUpdateListener> listeners = new ArrayList<>();
    private final List<TigerServerLogListener> logListeners = new ArrayList<>();
    private CfgServer configuration;
    private TigerServerStatus status = TigerServerStatus.NEW;

    // protected because implementing servers use this var
    protected final org.slf4j.Logger log;

    protected AbstractTigerServer(String hostname, String serverId, TigerTestEnvMgr tigerTestEnvMgr, CfgServer configuration) {
        this.hostname = hostname;
        this.serverId = serverId;
        this.tigerTestEnvMgr = tigerTestEnvMgr;
        this.configuration = configuration;
        log = org.slf4j.LoggerFactory.getLogger("TgrSrv-" + serverId);
        try {
            TigerServerLogManager.addAppenders(this);
        } catch (NoClassDefFoundError ncde) {
            log.warn("Unable to detect logback library! Log appender for server {} not activated", serverId);
        }
    }

    @SuppressWarnings("unused")
    public String getServerTypeToken() {
        try {
            return getClass().getAnnotation(TigerServerType.class).value();
        } catch (NullPointerException npe) {
            throw new TigerTestEnvException("Server class " + this.getClass() + " has no "
                + TigerServerType.class.getCanonicalName() + " Annotation!");
        }
    }

    static String determineHostname(CfgServer configuration, String serverId) {
        if (StringUtils.isNotBlank(configuration.getHostname())) {
            return configuration.getHostname();
        } else {
            return serverId;
        }
    }

    public void start(TigerTestEnvMgr testEnvMgr) {
        if (testEnvMgr.isShuttingDown()) {
            log.debug("Skipping startup, already shutting down...");
            synchronized (this) {
                setStatus(TigerServerStatus.STOPPED, getServerId() + " skipped startup");
            }
            return;
        }
        synchronized (this) {
            if (getStatus() != TigerServerStatus.NEW) {
                throw new TigerEnvironmentStartupException("Server %s was already started!", getServerId());
            }
        }
        publishNewStatusUpdate(TigerServerStatusUpdate.builder()
            .type(getServerTypeToken())
            .status(TigerServerStatus.STARTING)
            .statusMessage("Checking configuration " + getServerId() + "...").build());

        reloadConfiguration();

        assertThatConfigurationIsCorrect();

        configuration.getEnvironment().stream()
            .map(testEnvMgr::replaceSysPropsInString)
            .forEach(environmentProperties::add);

        if (configuration.getUrlMappings() != null && testEnvMgr.getLocalTigerProxyOptional().isPresent()) {
            statusMessage("Adding routes to local tiger proxy for server " + getServerId() + "...");
            configuration.getUrlMappings().forEach(mapping -> {
                if (StringUtils.isBlank(mapping) || !mapping.contains("-->") || mapping.split(" --> ", 2).length != 2) {
                    throw new TigerConfigurationException("The urlMappings configuration '" + mapping
                        + "' is not correct. Please check your .yaml-file.");
                }

                String[] routeParts = mapping.split(" --> ", 2);
                testEnvMgr.getLocalTigerProxyOptional().ifPresent(
                    proxy -> proxy.addRoute(TigerRoute.builder()
                        .from(routeParts[0])
                        .to(routeParts[1])
                        .build())
                );

            });
        }

        loadPkiForProxy();

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
            log.warn(String.format(t.getClass().getSimpleName() + " during startup of server %s. Used configuration was %s",
                getServerId(), TigerSerializationUtil.toJson(getConfiguration())), t);
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
            this.configuration = TigerGlobalConfiguration.instantiateConfigurationBeanStrict(getConfigurationBeanClass(),
                    "tiger", "servers", getServerId())
                .orElseThrow(
                    () -> new TigerEnvironmentStartupException("Could not reload configuration for server with id %s", getServerId()));
            tigerTestEnvMgr.getConfiguration().getServers().put(getServerId(), configuration);
        } catch (TigerConfigurationException e) {
            log.warn("Could not reload configuration for server {}", getServerId(), e);
        }
    }

    public Class<? extends CfgServer> getConfigurationBeanClass() {
        return CfgServer.class;
    }

    private void loadPkiForProxy() {
        if (!getConfiguration().getPkiKeys().isEmpty()) {
            log.info("Loading PKI resources for instance {}...", getServerId());
        }
        getConfiguration().getPkiKeys().stream()
            .filter(key -> key.getType() == PkiType.Certificate)
            .forEach(key -> {
                if (StringUtils.isBlank(key.getPem())) {
                    throw new TigerConfigurationException(
                        "Your certificate is empty, please check your .yaml-file for " + key.getId());
                }
                log.info("Adding certificate {}", key.getId());
                getTigerTestEnvMgr().getLocalTigerProxyOptional().ifPresent(proxy -> proxy.addKey(
                    key.getId(),
                    KeyMgr.readCertificateFromPem("-----BEGIN CERTIFICATE-----\n"
                        + key.getPem().replace(" ", "\n")
                        + "\n-----END CERTIFICATE-----").getPublicKey())
                );
            });
        getConfiguration().getPkiKeys().stream()
            .filter(key -> key.getType() == PkiType.Key)
            .forEach(key -> {
                if (StringUtils.isBlank(key.getPem())) {
                    throw new TigerConfigurationException(
                        "Your Key is empty, please check your .yaml-file for " + key.getId());
                }
                log.info("Adding key {}", key.getId());
                getTigerTestEnvMgr().getLocalTigerProxyOptional().ifPresent(proxy -> proxy.addKey(
                    key.getId(),
                    KeyMgr.readKeyFromPem("-----BEGIN PRIVATE KEY-----\n"
                        + key.getPem().replace(" ", "\n")
                        + "\n-----END PRIVATE KEY-----"))
                );
            });
    }

    public abstract void performStartup();

    protected void processExports() {
        configuration.getExports().forEach(exp -> {
            String[] kvp = exp.split("=", 2);
            // ports substitution are only supported for docker based instances
            kvp[1] = kvp[1].replace("${NAME}", getHostname());

            log.info("Setting global property {}={}", kvp[0], kvp[1]);
            TigerGlobalConfiguration.putValue(kvp[0], kvp[1], SourceType.RUNTIME_EXPORT);
        });
    }

    public void assertThatConfigurationIsCorrect() {
        if (StringUtils.isBlank(serverId)) {
            throw new TigerTestEnvException("Server Id must not be blank!");
        }
        assertCfgPropertySet(getConfiguration(), "type");

        // set default values for all types
        if (getConfiguration().getStartupTimeoutSec() == null) {
            log.info("Defaulting startup timeout sec to 20sec for server {}", serverId);
            getConfiguration().setStartupTimeoutSec(20);
        }
    }

    @SneakyThrows
    protected void assertCfgPropertySet(Object target, String... propertyNames) {
        for (String propertyName : propertyNames) {
            Method mthd = target.getClass()
                .getMethod("get" + Character.toUpperCase(propertyName.charAt(0))
                    + propertyName.substring(1));
            target = mthd.invoke(target);
            if (target == null) {
                throw new TigerTestEnvException(
                    "Server %s must have property %s be set and not be NULL!", getServerId(), propertyName);
            }
            if (target instanceof List) {
                assertListCfgPropertySet((List<?>) target, propertyName);
            } else if (target instanceof String && ((String) target).isBlank()) {
                throw new TigerTestEnvException(
                    "Server %s must have property %s be set and not be empty!", getServerId(), propertyName);
            }
        }
    }

    private void assertListCfgPropertySet(List<?> target, String propertyName) {
        if (target.isEmpty() || target.get(0) == null) {
            throw new TigerTestEnvException(
                "Server %s must have property %s be set and must contain at least one non empty entry", getServerId(), propertyName);
        }
        if (target.get(0) instanceof String && ((String) target.get(0)).isBlank()) {
            throw new TigerTestEnvException(
                "Server %s must have property %s be set and contain at least one non empty entry!", getServerId(), propertyName);
        }
    }

    public Optional<Integer> getStartupTimeoutSec() {
        return Optional.ofNullable(configuration.getStartupTimeoutSec());
    }

    public void addServerToLocalProxyRouteMap(URL url) {
        addRoute(TigerRoute.builder()
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

    void addRoute(TigerRoute newRoute) {
        getTigerTestEnvMgr().getLocalTigerProxyOptional().ifPresent(proxy -> {
            proxy.addRoute(newRoute);
            routes.add(newRoute);
        });
    }

    void removeAllRoutes() {
        getTigerTestEnvMgr().getLocalTigerProxyOptional().ifPresent(proxy -> {
            log.info("Removing routes for {}...", getServerId());
            routes.stream()
                .map(TigerRoute::getId)
                .forEach(proxy::removeRoute);
        });
    }

    public abstract void shutdown();

    public List<AbstractTigerServer> getDependUponList() {
        if (StringUtils.isBlank(getConfiguration().getDependsUpon())) {
            return List.of();
        }
        return Stream.of(getConfiguration().getDependsUpon().split(","))
            .filter(StringUtils::isNotBlank)
            .map(String::trim)
            .map(serverName -> tigerTestEnvMgr.findServer(serverName)
                .orElseThrow(() -> new TigerEnvironmentStartupException(
                    "Unknown server: '%s' in dependUponList of server '%s'", serverName, getServerId())))
            .collect(Collectors.toUnmodifiableList());
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
        publishNewStatusUpdate(TigerServerStatusUpdate.builder()
            .status(newStatus)
            .statusMessage(statusMessage)
            .build());
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
        publishNewStatusUpdate(TigerServerStatusUpdate.builder()
            .statusMessage(statusMessage)
            .build());
        if (log.isInfoEnabled()) {
            log.info(Ansi.colorize(statusMessage, RbelAnsiColors.GREEN_BOLD));
        }
    }

    public void publishNewStatusUpdate(TigerServerStatusUpdate update) {
        update.setType(getServerTypeToken());
        tigerTestEnvMgr.publishStatusUpdateToListeners(TigerStatusUpdate.builder()
                        .serverUpdate(new LinkedHashMap<>(Map.of(serverId, update)))
                        .build(), listeners);
    }

    @SuppressWarnings("unused")
    protected String findCommandInPath(String command) {
        if (System.getenv("PATH") == null) {
            throw new TigerEnvironmentStartupException("No PATH variable set, unable to find helm and kubectl commands!");
        }
        return Arrays.stream(System.getenv("PATH").split(File.pathSeparator))
            .map(folder -> folder + File.separator + command)
            .filter(file -> new File(file).canExecute())
            .findFirst().orElseThrow(() -> new TigerEnvironmentStartupException("Unable to locate script '%s'", command));
    }
}
