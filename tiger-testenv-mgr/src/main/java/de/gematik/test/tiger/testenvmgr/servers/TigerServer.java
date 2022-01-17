package de.gematik.test.tiger.testenvmgr.servers;

import de.gematik.test.tiger.common.config.CfgExternalJarOptions;
import de.gematik.test.tiger.common.config.PkiType;
import de.gematik.test.tiger.common.config.ServerType;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.pki.KeyMgr;
import de.gematik.test.tiger.testenvmgr.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.platform.commons.util.StringUtils;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@RequiredArgsConstructor
@Slf4j
@Getter
public abstract class TigerServer {

    public static final int DEFAULT_STARTUP_TIMEOUT_IN_SECONDS = 20;

    private final String hostname;
    private final String serverId;
    private final CfgServer configuration;
    private final List<String> environmentProperties = new ArrayList<>();
    private final List<TigerRoute> routes = new ArrayList<>();
    private final TigerTestEnvMgr tigerTestEnvMgr;
    private boolean started = false;

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
            if (started) {
                throw new TigerEnvironmentStartupException("Server " + getServerId() + " is already running!");
            }
            started = true;
        }

        assertThatConfigurationIsCorrect();

        final ServerType type = configuration.getType();

        // replace sys props in environment
        configuration.getEnvironment().stream()
            .map(testEnvMgr::replaceSysPropsInString)
            .forEach(environmentProperties::add);

        // apply routes to local proxy
        if (configuration.getUrlMappings() != null) {
            configuration.getUrlMappings().forEach(mapping -> {
                if (StringUtils.isBlank(mapping) || !mapping.contains("-->") || mapping.split(" --> ", 2).length != 2) {
                    throw new TigerConfigurationException("The urlMappings configuration '" + mapping + "' is not correct. Please check your .yaml-file.");
                }

                String[] routeParts = mapping.split(" --> ", 2);
                testEnvMgr.getLocalTigerProxy().addRoute(TigerRoute.builder()
                    .from(routeParts[0])
                    .to(routeParts[1])
                    .build());

            });
        }

        loadPkiForProxy();

        performStartup();

        // TGR-284 set system properties from exports section and store the value in environmentVariables map
        // replace ${NAME} with server host name
        //
        configuration.getExports().forEach(exp -> {
            String[] kvp = exp.split("=", 2);
            // ports substitution are only supported for docker based instances
            if (type == ServerType.DOCKER && configuration.getDockerOptions().getPorts() != null) {
                configuration.getDockerOptions().getPorts().forEach((localPort, externPort) ->
                    kvp[1] = kvp[1].replace("${PORT:" + localPort + "}", String.valueOf(externPort))
                );
            }
            kvp[1] = kvp[1].replace("${NAME}", getHostname());

            log.info("  setting system property " + kvp[0] + "=" + kvp[1]);
            System.setProperty(kvp[0], kvp[1]);
        });
    }

    private void loadPkiForProxy() {
        log.info("  loading PKI resources for instance {}...", getHostname());
        getConfiguration().getPkiKeys().stream()
            .filter(key -> key.getType() == PkiType.Certificate)
            .forEach(key -> {
                if (StringUtils.isBlank(key.getPem())) {
                    throw new TigerConfigurationException(
                        "Your certificate is empty, please check your .yaml-file for " + key.getId());
                }
                log.info("Adding certificate " + key.getId());
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
                log.info("Adding key " + key.getId());
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
        assertThat(serverId).withFailMessage("Server Id must not be blank!").isNotBlank();
        if (this instanceof DockerComposeServer
            && StringUtils.isNotBlank(getHostname())) {
            throw new TigerConfigurationException("Docker compose does not support a hostname for the node!");
        }

        assertCfgPropertySet(getConfiguration(), "type");

        if (type != ServerType.EXTERNALJAR && type != ServerType.EXTERNALURL && type != ServerType.DOCKER_COMPOSE) {
            assertCfgPropertySet(getConfiguration(), "version");
        }

        // set default value for Tiger Proxy source
        if (type == ServerType.TIGERPROXY && (getConfiguration().getSource() == null || getConfiguration().getSource()
            .isEmpty())) {
            log.info("Defaulting tiger proxy source to gematik nexus for " + serverId);
            getConfiguration().setSource(new ArrayList<>(List.of("nexus")));
        }
        // set default value for Tiger Proxy external Jar options
        if (type == ServerType.TIGERPROXY && getConfiguration().getExternalJarOptions() == null) {
            getConfiguration().setExternalJarOptions(new CfgExternalJarOptions());
        }

        // set default value for Tiger Proxy healthcheck
        if (type == ServerType.TIGERPROXY && getConfiguration().getExternalJarOptions().getHealthcheck() == null) {
            getConfiguration().getExternalJarOptions()
                .setHealthcheck("http://127.0.0.1:" + getConfiguration().getTigerProxyCfg().getServerPort());
        }

        // set default values for all types
        if (getConfiguration().getStartupTimeoutSec() == null) {
            log.info("Defaulting startup timeout sec to 20sec for server " + serverId);
            getConfiguration().setStartupTimeoutSec(20);
        }

        // defaulting work dir to temp folder on system if not set in config
        if (type == ServerType.EXTERNALJAR || type == ServerType.TIGERPROXY) {
            String folder = getConfiguration().getExternalJarOptions().getWorkingDir();
            if (folder == null) {
                folder = Path.of(System.getProperty("java.io.tmpdir"), "tiger_downloads").toFile().getAbsolutePath();
                log.info("Defaulting to temp folder '" + folder + "' as work dir for server " + serverId);
                getConfiguration().getExternalJarOptions().setWorkingDir(folder);
            }
            File f = new File(folder);
            if (!f.exists()) {
                if (!f.mkdirs()) {
                    throw new TigerTestEnvException("Unable to create working dir folder " + f.getAbsolutePath());
                }
            }
        }

        if (type != ServerType.TIGERPROXY) {
            assertCfgPropertySet(getConfiguration(), "source");
        }

        if (type == ServerType.EXTERNALJAR) {
            assertCfgPropertySet(getConfiguration().getExternalJarOptions(), "healthcheck");
        }

        if (type == ServerType.TIGERPROXY) {
            if (getConfiguration().getTigerProxyCfg().getServerPort() < 1) {
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
            List<?> l = (List<?>) value;
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

    public Optional<Integer> getStartupTimeoutSec() {
        return Optional.ofNullable(configuration.getStartupTimeoutSec());
    }

    void addServerToLocalProxyRouteMap(URL url) {
        try {
            int port = url.getPort();
            if (port == -1) {
                port = url.getDefaultPort();
            }
            addRoute(TigerRoute.builder()
                .from(TigerTestEnvMgr.HTTP + getHostname())
                .to(url.toURI().getScheme() + "://" + url.getHost() + ":" + port)
                .build());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Error while convert to URI: '" + url + "'", e);
        }
    }

    void addRoute(TigerRoute newRoute) {
        getTigerTestEnvMgr().getLocalTigerProxy().addRoute(newRoute);
        routes.add(newRoute);
    }

    void removeAllRoutes() {
        log.info("Removing routes for {}...", getHostname());
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
}
