/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.servers;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.config.ServerType;
import de.gematik.test.tiger.common.config.SourceType;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.PkiType;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
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
import java.io.File;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.platform.commons.util.StringUtils;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.util.SocketUtils;

@Getter
public abstract class TigerServer implements TigerEnvUpdateSender {

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

    protected final org.slf4j.Logger log;

    public TigerServer(String hostname, String serverId, TigerTestEnvMgr tigerTestEnvMgr, CfgServer configuration) {
        this.hostname = hostname;
        this.serverId = serverId;
        this.tigerTestEnvMgr = tigerTestEnvMgr;
        this.configuration = configuration;
        log = org.slf4j.LoggerFactory.getLogger("TgrSrv-" + serverId);
        TigerServerLogManager.addAppenders(this);
    }

    public static TigerServer create(String serverId, CfgServer configuration, TigerTestEnvMgr tigerTestEnvMgr) {
        if (configuration.getType() == null) {
            throw new TigerTestEnvException("No server type configured for server '" + serverId + "'");
        }

        switch (configuration.getType()) {
            case DOCKER:
                return DockerServer.builder()
                    .configuration(configuration)
                    .tigerTestEnvMgr(tigerTestEnvMgr)
                    .serverId(serverId)
                    .build();
            case DOCKER_COMPOSE:
                return DockerComposeServer.builder()
                    .configuration(configuration)
                    .tigerTestEnvMgr(tigerTestEnvMgr)
                    .serverId(serverId)
                    .build();
            case EXTERNALURL:
                return ExternalUrlServer.builder()
                    .configuration(configuration)
                    .serverId(serverId)
                    .tigerTestEnvMgr(tigerTestEnvMgr)
                    .build();
            case EXTERNALJAR:
                return ExternalJarServer.builder()
                    .configuration(configuration)
                    .serverId(serverId)
                    .tigerTestEnvMgr(tigerTestEnvMgr)
                    .build();
            case TIGERPROXY:
                return new TigerProxyServer(serverId, configuration, tigerTestEnvMgr);
            default:
                throw new TigerTestEnvException(
                    String.format("Unsupported server type %s found in server %s", configuration.getType(), serverId));
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
        synchronized (this) {
            if (getStatus() != TigerServerStatus.NEW) {
                throw new TigerEnvironmentStartupException("Server " + getServerId() + " was already started!");
            }
        }
        setStatus(TigerServerStatus.STARTING, "Starting " + getServerId());

        reloadConfiguration();

        assertThatConfigurationIsCorrect();

        final ServerType type = configuration.getType();

        configuration.getEnvironment().stream()
            .map(testEnvMgr::replaceSysPropsInString)
            .forEach(environmentProperties::add);

        // apply routes to local proxy
        if (configuration.getUrlMappings() != null) {
            configuration.getUrlMappings().forEach(mapping -> {
                if (StringUtils.isBlank(mapping) || !mapping.contains("-->") || mapping.split(" --> ", 2).length != 2) {
                    throw new TigerConfigurationException("The urlMappings configuration '" + mapping
                        + "' is not correct. Please check your .yaml-file.");
                }

                String[] routeParts = mapping.split(" --> ", 2);
                testEnvMgr.getLocalTigerProxy().addRoute(TigerRoute.builder()
                    .from(routeParts[0])
                    .to(routeParts[1])
                    .build());

            });
        }

        loadPkiForProxy();

        try {
            performStartup();
        } catch (RuntimeException e) {
            log.warn(String.format("Error during startup of server %s. Used configuration was %s",
                getServerId(), TigerSerializationUtil.toJson(getConfiguration())), e);
            throw e;
        } catch (Throwable t) {
            log.warn(String.format("Throwable during startup of server %s. Used configuration was %s",
                getServerId(), TigerSerializationUtil.toJson(getConfiguration())), t);
            throw t;
        }
        statusMessage(getServerId() + " started");

        configuration.getExports().forEach(exp -> {
            String[] kvp = exp.split("=", 2);
            // ports substitution are only supported for docker based instances
            if (type == ServerType.DOCKER && configuration.getDockerOptions().getPorts() != null) {
                configuration.getDockerOptions().getPorts().forEach((localPort, externPort) ->
                    kvp[1] = kvp[1].replace("${PORT:" + localPort + "}", String.valueOf(externPort))
                );
            }
            kvp[1] = kvp[1].replace("${NAME}", getHostname());

            log.info("Setting global property {}={}", kvp[0], kvp[1]);
            TigerGlobalConfiguration.putValue(kvp[0], kvp[1], SourceType.RUNTIME_EXPORT);
        });

        synchronized (this) {
            setStatus(TigerServerStatus.RUNNING, getServerId() + " READY");
        }
    }

    private void reloadConfiguration() {
        try {
            this.configuration = TigerGlobalConfiguration.instantiateConfigurationBean(CfgServer.class,
                    "tiger", "servers", getServerId())
                .orElseThrow(
                    () -> new TigerEnvironmentStartupException("Could not reload configuration for server with id " + getServerId()));
            tigerTestEnvMgr.getConfiguration().getServers().put(getServerId(), configuration);
        } catch (TigerConfigurationException e) {
            log.warn("Could not reload configuration for server {}", getServerId(), e);
        }
    }

    private void loadPkiForProxy() {
        log.info("Loading PKI resources for instance {}...", getServerId());
        getConfiguration().getPkiKeys().stream()
            .filter(key -> key.getType() == PkiType.Certificate)
            .forEach(key -> {
                if (StringUtils.isBlank(key.getPem())) {
                    throw new TigerConfigurationException(
                        "Your certificate is empty, please check your .yaml-file for " + key.getId());
                }
                log.info("Adding certificate {}", key.getId());
                getTigerTestEnvMgr().getLocalTigerProxy().addKey(
                    key.getId(),
                    KeyMgr.readCertificateFromPem("-----BEGIN CERTIFICATE-----\n"
                        + key.getPem().replace(" ", "\n")
                        + "\n-----END CERTIFICATE-----").getPublicKey());
            });
        getConfiguration().getPkiKeys().stream()
            .filter(key -> key.getType() == PkiType.Key)
            .forEach(key -> {
                if (StringUtils.isBlank(key.getPem())) {
                    throw new TigerConfigurationException(
                        "Your Key is empty, please check your .yaml-file for " + key.getId());
                }
                log.info("Adding key {}", key.getId());
                getTigerTestEnvMgr().getLocalTigerProxy().addKey(
                    key.getId(),
                    KeyMgr.readKeyFromPem("-----BEGIN PRIVATE KEY-----\n"
                        + key.getPem().replace(" ", "\n")
                        + "\n-----END PRIVATE KEY-----"));
            });
    }

    public abstract void performStartup();

    public void assertThatConfigurationIsCorrect() {
        var type = getConfiguration().getType();
        assertThat(serverId)
            .withFailMessage("Server Id must not be blank!")
            .isNotBlank();

        if (this instanceof DockerComposeServer && StringUtils.isNotBlank(getHostname())) {
            throw new TigerConfigurationException("Docker compose does not support a hostname for the node!");
        }

        assertCfgPropertySet(getConfiguration(), "type");

        if (type == ServerType.DOCKER) {
            assertCfgPropertySet(getConfiguration(), "version");
        }

        // assert that server-port is set for the Tiger Proxy
        if (type == ServerType.TIGERPROXY) {
            if (getConfiguration().getTigerProxyCfg() == null) {
                getConfiguration().setTigerProxyCfg(new TigerProxyConfiguration());
            }
            if (getConfiguration().getTigerProxyCfg().getAdminPort() <= 0) {
                getConfiguration().getTigerProxyCfg().setAdminPort(SocketUtils.findAvailableTcpPort());
            }
            if (getConfiguration().getTigerProxyCfg().getProxyPort() == null
                || getConfiguration().getTigerProxyCfg().getProxyPort() <= 0) {
                throw new TigerTestEnvException("Missing proxy-port configuration for server '" + getServerId() + "'");
            }
        }

        // set default values for all types
        if (getConfiguration().getStartupTimeoutSec() == null) {
            log.info("Defaulting startup timeout sec to 20sec for server {}", serverId);
            getConfiguration().setStartupTimeoutSec(20);
        }

        if (type != ServerType.TIGERPROXY) {
            assertCfgPropertySet(getConfiguration(), "source");
        }

        // defaulting work dir to temp folder on system if not set in config
        if (type == ServerType.EXTERNALJAR) {
            if (getConfiguration().getExternalJarOptions() != null) {
                String folder = getConfiguration().getExternalJarOptions().getWorkingDir();
                if (folder == null) {
                    if (getConfiguration().getSource().get(0).startsWith("local:")) {
                        final String jarPath = getConfiguration().getSource().get(0).split("local:")[1];
                        folder = Paths.get(jarPath).toAbsolutePath().getParent().toString();
                        getConfiguration().getSource().add(0,
                            "local:" + jarPath.substring(jarPath.lastIndexOf('/')));
                        log.info("Defaulting to parent folder '{}' as working directory for server {}", folder, serverId);
                    } else {
                        folder = Path.of(System.getProperty("java.io.tmpdir"), "tiger_ls").toFile()
                            .getAbsolutePath();
                        log.info("Defaulting to temp folder '{}' as working directory for server {}", folder, serverId);
                    }
                    getConfiguration().getExternalJarOptions().setWorkingDir(folder);
                }
                File f = new File(folder);
                if (!f.exists()) {
                    if (!f.mkdirs()) {
                        throw new TigerTestEnvException("Unable to create working dir folder " + f.getAbsolutePath());
                    }
                }
            }
        }

        if (type == ServerType.EXTERNALJAR) {
            assertCfgPropertySet(getConfiguration(), "healthcheckUrl");
        }
    }

    @SneakyThrows
    private void assertCfgPropertySet(Object target, String... propertyNames) {
        for (String propertyName : propertyNames) {
            Method mthd = target.getClass()
                .getMethod("get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1));
            target = mthd.invoke(target);
            if (target == null) {
                throw new TigerTestEnvException("Server " + getServerId() + " must have property " + propertyName
                    + " be set and not be NULL!");
            }
            if (target instanceof List) {
                List<?> l = (List<?>) target;
                if (l.isEmpty() ||
                    l.get(0) == null) {
                    throw new TigerTestEnvException(
                        "Server " + getServerId() + " must have property " + propertyName
                            + " be set and must contain at least one not empty entry!");
                }
                if (l.get(0) instanceof String) {
                    if (((String) l.get(0)).isBlank()) {
                        throw new TigerTestEnvException(
                            "Server " + getServerId() + " must have property " + propertyName
                                + " be set and contain at least one not empty entry!");
                    }
                }
            } else {
                if (target instanceof String) {
                    if (((String) target).isBlank()) {
                        throw new TigerTestEnvException(
                            "Server " + getServerId() + " must have property " + propertyName
                                + " be set and not be empty!");
                    }
                }
            }
        }
    }

    public Optional<Integer> getStartupTimeoutSec() {
        return Optional.ofNullable(configuration.getStartupTimeoutSec());
    }

    void addServerToLocalProxyRouteMap(URL url) {
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
        getTigerTestEnvMgr().getLocalTigerProxy().addRoute(newRoute);
        routes.add(newRoute);
    }

    void removeAllRoutes() {
        log.info("Removing routes for {}...", getServerId());
        routes.stream()
            .map(TigerRoute::getId)
            .forEach(getTigerTestEnvMgr().getLocalTigerProxy()::removeRoute);
    }

    public abstract void shutdown();

    public List<TigerServer> getDependUponList() {
        if (StringUtils.isBlank(getConfiguration().getDependsUpon())) {
            return List.of();
        }
        return Stream.of(getConfiguration().getDependsUpon().split(","))
            .filter(StringUtils::isNotBlank)
            .map(String::trim)
            .map(serverName -> tigerTestEnvMgr.findServer(serverName)
                .orElseThrow(() -> new TigerEnvironmentStartupException(
                    "Unknown server: '" + serverName + "' in dependUponList of server '" + getServerId() + "'")))
            .collect(Collectors.toUnmodifiableList());
    }

    public String getDestinationUrl(String fallbackProtocol) {
        throw new TigerTestEnvException(
            "Sophisticated reverse proxy for '" + getClass().getSimpleName() + "' is not supported!");
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
        if (statusMessage != null) {
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
        log.info(Ansi.colorize(statusMessage, RbelAnsiColors.GREEN_BOLD));
    }

    void publishNewStatusUpdate(TigerServerStatusUpdate update) {
        if (tigerTestEnvMgr.getExecutor() != null) {
            tigerTestEnvMgr.getExecutor().submit(
                () -> listeners.parallelStream()
                    .forEach(listener -> listener.receiveTestEnvUpdate(TigerStatusUpdate.builder()
                        .serverUpdate(new LinkedHashMap<>(Map.of(serverId, update)))
                        .build()))
            );
        }
    }
}
