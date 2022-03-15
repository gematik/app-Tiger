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

import static org.awaitility.Awaitility.await;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.common.config.SourceType;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.Configuration;
import de.gematik.test.tiger.testenvmgr.servers.TigerServer;
import java.io.*;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

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
    private final ExecutorService executor = Executors
        .newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

    public TigerTestEnvMgr() {
        Configuration configuration = readConfiguration();

        dockerManager = new DockerMgr();

        localTigerProxy = startLocalTigerProxy(configuration);

        if (configuration.isLocalProxyActive()) {
            log.info("Started local tiger proxy on port " + localTigerProxy.getPort() + "...");
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

    private TigerProxy startLocalTigerProxy(Configuration configuration) {
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
        localTigerProxy = new TigerProxy(configuration.getTigerProxy());
        return localTigerProxy;
    }

    public static void main(String[] args) {
        TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        try {
            envMgr.setUpEnvironment();
        } catch (Exception e) {
            log.error("Error while starting up stand alone tiger testenv mgr! ABORTING...", e);
            System.exit(1);
        }
        log.info(Ansi.colorize("Tiger standalone test environment UP!", RbelAnsiColors.GREEN_BOLD));
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
            "tiger");
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
                servers.put(serverEntry.getKey(),
                    TigerServer.create(serverEntry.getKey(), serverEntry.getValue(), this));
            }
        }
    }

    @Override
    public void setUpEnvironment() {
        assertNoCyclesInGraph();
        assertNoUnknownServersInDependencies();

        log.info("starting set up of test environment...");

        final List<TigerServer> initialServersToBoot = servers.values().parallelStream()
            .filter(server -> server.getDependUponList().isEmpty())
            .collect(Collectors.toList());

        log.info("Starting setup by triggering boot of following server: {}",
            initialServersToBoot.stream()
                .map(TigerServer::getHostname)
                .collect(Collectors.toList()));

        initialServersToBoot.parallelStream()
            .forEach(this::startServer);

        localTigerProxy.subscribeToTrafficEndpoints(configuration.getTigerProxy());

        log.info(Ansi.colorize("finished set up test environment OK", RbelAnsiColors.GREEN_BOLD));
    }

    private void assertNoUnknownServersInDependencies() {
        getServers().values().stream()
            .forEach(TigerServer::getDependUponList);
    }

    private void startServer(TigerServer server) {
        synchronized (server) { //NOSONAR
            // we REALLY want to synchronize ONLY on the server!
            if (server.getStatus() != TigerServer.TigerServerStatus.NEW) {
                return;
            }
            server.start(this);
        }

        servers.values().parallelStream()
            .peek(toBeStartedServer -> log.debug("Considering starting server {} with status {}...",
                toBeStartedServer.getHostname(), toBeStartedServer.getStatus()))
            .filter(candidate -> candidate.getStatus() == TigerServer.TigerServerStatus.NEW)
            .filter(candidate -> candidate.getDependUponList().stream()
                .filter(depending -> depending.getStatus() != TigerServer.TigerServerStatus.RUNNING)
                .findAny().isEmpty())
            .peek(toBeStartedServer -> log.info("About to start server {} with status {}",
                toBeStartedServer.getHostname(), toBeStartedServer.getStatus()))
            .forEach(this::startServer);
    }

    public String replaceSysPropsInString(String str) {
        return str;
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

    public boolean isLocalTigerProxyActive() {
        if (configuration == null) {
            return true;
        }
        return configuration.isLocalProxyActive();
    }
}
