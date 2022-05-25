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

package de.gematik.test.tiger.testenvmgr.env;

import static org.awaitility.Awaitility.await;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.ResponseItem;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import de.gematik.test.tiger.testenvmgr.servers.DockerComposeServer;
import de.gematik.test.tiger.testenvmgr.servers.DockerServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServer;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.utility.Base58;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Slf4j
@Getter
public class DockerMgr {

    @SuppressWarnings("OctalInteger")
    private static final int MOD_ALL_EXEC = 0777;

    private static final String CLZPATH = "classpath:";

    private final Map<String, GenericContainer<?>> containers = new HashMap<>();

    @SuppressWarnings("unused")
    public void startContainer(final DockerServer server) {
        String imageName = buildImageName(server);
        var testContainersImageName = DockerImageName.parse(imageName);

        pullImage(imageName);

        final var container = new GenericContainer<>(testContainersImageName); //NOSONAR
        try {
            InspectImageResponse iiResponse = container.getDockerClient().inspectImageCmd(imageName).exec();
            final ContainerConfig containerConfig = iiResponse.getConfig();
            if (containerConfig == null) {
                throw new TigerTestEnvException("Docker image '" + imageName + "' has no configuration info!");
            }
            if (server.getDockerOptions().isProxied()) {
                String[] startCmd = containerConfig.getCmd();
                String[] entryPointCmd = containerConfig.getEntrypoint();
                if (StringUtils.isNotEmpty(server.getDockerOptions().getEntryPoint())) {
                    entryPointCmd = new String[]{server.getDockerOptions().getEntryPoint()};
                }
                // erezept hardcoded
                if (entryPointCmd != null && entryPointCmd[0].equals("/bin/sh") && entryPointCmd[1].equals("-c")) {
                    entryPointCmd = new String[]{"su", containerConfig.getUser(), "-c",
                        "'" + entryPointCmd[2] + "'"};
                }
                File tmpScriptFolder = Path.of("target", "tiger-testenv-mgr").toFile();
                if (!tmpScriptFolder.exists()) {
                    if (!tmpScriptFolder.mkdirs()) {
                        throw new TigerTestEnvException(
                            "Unable to create temp folder for modified startup script for server "
                                + server.getServerId());
                    }
                }
                final String scriptName = createContainerStartupScript(server, iiResponse, startCmd, entryPointCmd);
                String containerScriptPath = containerConfig.getWorkingDir() + "/" + scriptName;
                container.withExtraHost("host.docker.internal", "host-gateway");

                container.withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of(tmpScriptFolder.getAbsolutePath(), scriptName), MOD_ALL_EXEC),
                    containerScriptPath);
                container.withCreateContainerCmdModifier(
                    cmd -> cmd.withUser("root").withEntrypoint(containerScriptPath));
            }

            container.setLogConsumers(List.of(new Slf4jLogConsumer(log)));
            log.info("Passing in environment for {}...", server.getServerId());
            addEnvVarsToContainer(container, server.getEnvironmentProperties());

            if (containerConfig.getExposedPorts() != null) {
                List<Integer> ports = Arrays.stream(containerConfig.getExposedPorts())
                    .map(ExposedPort::getPort)
                    .collect(Collectors.toList());
                log.info("Exposing ports for {}: {}", server.getServerId(), ports);
                container.setExposedPorts(ports);
            }
            if (server.getDockerOptions().isOneShot()) {
                container.withStartupCheckStrategy(new OneShotStartupCheckStrategy());
            }
            container.start();
            // make startup time and intervall and url (supporting ${PORT} and regex content configurable
            waitForHealthyStartup(server, container);
            container.getDockerClient().renameContainerCmd(container.getContainerId())
                .withName("tiger." + server.getServerId()).exec();
            containers.put(server.getServerId(), container);
            final Map<Integer, Integer> ports = new HashMap<>();
            // TODO TGR-282 LO PRIO for now we assume ports are bound only to one other port on the docker container
            // maybe just make clear we support only single exported port
            container.getContainerInfo().getNetworkSettings().getPorts().getBindings().entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .forEach(entry -> ports.put(entry.getKey().getPort(),
                    Integer.valueOf(entry.getValue()[0].getHostPortSpec())));
            server.getDockerOptions().setPorts(ports);
        } catch (final DockerException de) {
            throw new TigerTestEnvException("Failed to start container for server " + server.getServerId(), de);
        }
    }

    private String buildImageName(DockerServer server) {
        String result = server.getDockerSource();
        if (server.getTigerTestEnvMgr() == null) {
            result = server.getTigerTestEnvMgr().replaceSysPropsInString(server.getDockerSource());
        }
        if (server.getConfiguration().getVersion() != null) {
            result += ":" + server.getConfiguration().getVersion();
        }
        return result;
    }

    public void startComposition(final DockerComposeServer server) {
        List<String> composeFileContents = new ArrayList<>();
        File[] composeFiles = collectAndProcessComposeYamlFiles(server.getServerId(), server.getSource(), composeFileContents);
        String identity = "tiger_" + Base58.randomString(6).toLowerCase();
        DockerComposeContainer composition = new DockerComposeContainer(identity, composeFiles); //NOSONAR

        composeFileContents.stream()
            .filter(content -> !content.isEmpty())
            .map(content -> TigerSerializationUtil.yamlToJsonObject(content).getJSONObject("services"))
            .map(JSONObject::toMap)
            .flatMap(services -> services.entrySet().stream())
            .forEach(serviceEntry -> {
                var map = ((Map<String, ?>) serviceEntry.getValue());
                if (map.containsKey("ports")) {
                    ((List<Integer>) map.get("expose")).forEach(
                        port -> {
                            log.info("Exposing service {} with port {}", serviceEntry.getKey(), port);
                            composition.withExposedService(serviceEntry.getKey(), port);
                        }
                    );
                }
                composition.withLogConsumer(serviceEntry.getKey(), new Slf4jLogConsumer(log));
            });
        // ALERT! Jenkins only works with local docker compose!
        // So do not change this unless you VERIFIED it also works on Jenkins builds
        composition.withLocalCompose(true).withOptions("--compatibility").start(); //NOSONAR

        composeFileContents.stream()
            .filter(content -> !content.isEmpty())
            .map(content -> TigerSerializationUtil.yamlToJsonObject(content).getJSONObject("services"))
            .map(JSONObject::toMap)
            .flatMap(services -> services.entrySet().stream())
            .forEach(serviceEntry -> {
                var map = ((Map<String, ?>) serviceEntry.getValue());
                if (map.containsKey("expose")) {
                    ((List<Integer>) map.get("expose")).forEach(port -> {
                            log.info("Service {} with port {} exposed via {}", serviceEntry.getKey(), port,
                                composition.getServicePort(serviceEntry.getKey(), port));
                            ListContainersCmd cmd = DockerClientFactory.instance().client()
                                .listContainersCmd();
                            log.debug("Inspecting docker container: {}", cmd.exec().toString());
                        }
                    );
                }
                composition.withLogConsumer(serviceEntry.getKey(), new Slf4jLogConsumer(log));
            });

    }

    private File[] collectAndProcessComposeYamlFiles(String serverId, List<String> composeFilePaths, List<String> composeFileContents) {
        var folder = Paths.get("target", "tiger-testenv-mgr", serverId).toFile();
        return composeFilePaths.stream()
            .map(filePath -> {
                String content = readAndProcessComposeFile(filePath, composeFileContents);
                return saveComposeContentToTempFile(folder, filePath, content);
            }).toArray(File[]::new);
    }

    private String readAndProcessComposeFile(String filePath, List<String> composeFileContents) {
        try {
            String content;
            if (filePath.startsWith(CLZPATH)) {
                try (InputStream is = getClass().getResourceAsStream(filePath)) {
                    if (is == null) {
                        throw new TigerTestEnvException("Missing docker compose file in classpath " + filePath);
                    }
                    content = IOUtils.toString(is, StandardCharsets.UTF_8);
                } catch (IOException ioe) {
                    throw new TigerTestEnvException("Unable to create temp docker compose file (" + filePath + ")", ioe);
                }
            } else {
                content = FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8);
            }

            content = TigerGlobalConfiguration.resolvePlaceholders(content);
            composeFileContents.add(content);
            return content;
        } catch (IOException e) {
            throw new TigerTestEnvException("Unable to process compose file " + filePath, e);
        }
    }

    private File saveComposeContentToTempFile(File folder, String filePath, String content) {
        try {
            if (filePath.startsWith(CLZPATH)) {
                filePath = filePath.substring(CLZPATH.length());
            }
            var tmpFile = Paths.get(folder.getAbsolutePath(), filePath).toFile();
            if (!tmpFile.getParentFile().exists() && !tmpFile.getParentFile().mkdirs()) {
                throw new TigerTestEnvException(
                    "Unable to create temp folder " + tmpFile.getParentFile().getAbsolutePath());
            }
            FileUtils.writeStringToFile(tmpFile, content, StandardCharsets.UTF_8);
            return tmpFile;
        } catch (IOException e) {
            throw new TigerTestEnvException("Unable to process compose file " + filePath, e);
        }
    }


    private void addEnvVarsToContainer(GenericContainer<?> container, List<String> envVars) {
        envVars.stream()
            .filter(i -> i.contains("="))
            .map(i -> i.split("=", 2))
            .peek(envvar -> log.info("  * {}={}", envvar[0], envvar[1]))
            .forEach(envvar -> container.addEnv(
                TigerGlobalConfiguration.resolvePlaceholders(envvar[0]),
                TigerGlobalConfiguration.resolvePlaceholders(envvar[1])));
    }

    public void pullImage(final String imageName) {
        log.info("Pulling docker image {}...", imageName);
        final AtomicBoolean pullComplete = new AtomicBoolean();
        pullComplete.set(false);
        final AtomicReference<Throwable> cbException = new AtomicReference<>();
        DockerClientFactory.instance().client().pullImageCmd(imageName)
            .exec(new ResultCallback.Adapter<PullResponseItem>() {
                @Override
                public void onNext(PullResponseItem item) {
                    if (log.isDebugEnabled()) {
                        log.debug("{} {}", item.getStatus(),
                            Optional.ofNullable(item.getProgressDetail())
                                .map(ResponseItem.ProgressDetail::getCurrent)
                                .map(Object::toString)
                                .orElse(""));
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    cbException.set(throwable);
                }

                @Override
                public void onComplete() {
                    pullComplete.set(true);
                }
            });

        await().pollInterval(Duration.ofMillis(1000)).atMost(5, TimeUnit.MINUTES).until(() -> {
            if (cbException.get() != null) {
                throw new TigerTestEnvException("Unable to pull image " + imageName + "!", cbException.get());
            }
            return pullComplete.get();
        });
        log.info("Docker image {} is available locally!", imageName);
    }

    private String createContainerStartupScript(TigerServer server, InspectImageResponse iiResponse, String[] startCmd,
        String[] entryPointCmd) {
        final ContainerConfig containerConfig = iiResponse.getConfig();
        if (containerConfig == null) {
            throw new TigerTestEnvException(
                "Docker image of server '" + server.getServerId() + "' has no configuration info!");
        }
        startCmd = startCmd == null ? new String[0] : startCmd;
        entryPointCmd = entryPointCmd == null ? new String[0] : entryPointCmd;
        try {
            final var proxycert = IOUtils.toString(
                Objects.requireNonNull(getClass().getResourceAsStream("/CertificateAuthorityCertificate.pem")),
                StandardCharsets.UTF_8);
            final var lecert = IOUtils.toString(
                Objects.requireNonNull(getClass().getResourceAsStream("/letsencrypt.crt")),
                StandardCharsets.UTF_8);
            final var risecert = IOUtils.toString(
                Objects.requireNonNull(getClass().getResourceAsStream("/idp-rise-tu.crt")),
                StandardCharsets.UTF_8);

            File tmpScriptFolder = Path.of("target", "tiger-testenv-mgr").toFile();
            if (!tmpScriptFolder.exists() && !tmpScriptFolder.mkdirs()) {
                throw new TigerTestEnvException("Unable to create script folder " + tmpScriptFolder.getAbsolutePath());
            }
            final var scriptName = "__tigerStart_" + server.getServerId() + ".sh";
            var content = "#!/bin/sh -x\nenv\n"
                // append proxy and other certs (for rise idp)
                + "echo \"" + proxycert + "\" >> /etc/ssl/certs/ca-certificates.crt\n"
                + "echo \"" + lecert + "\" >> /etc/ssl/certs/ca-certificates.crt\n"
                + "echo \"" + risecert + "\" >> /etc/ssl/certs/ca-certificates.crt\n";

            // testing ca cert of proxy with openssl
            //+ "echo \"" + proxycert + "\" > /tmp/chain.pem\n"
            //+ "openssl s_client -connect localhost:7000 -showcerts --proxy host.docker.internal:"
            //+ envmgr.getLocalDockerProxy().getPort() + " -CAfile /tmp/chain.pem\n"
            // idp-test.zentral.idp.splitdns.ti-dienste.de:443
            // testing ca cert of proxy with rust client
            // WEBCLIENT + "RUST_LOG=trace /usr/bin/webclient http://tsl \n"

            // change to working dir and execute former entrypoint/startcmd
            content += getContainerWorkingDirectory(containerConfig)
                + String.join(" ", entryPointCmd).replace("\t", " ")
                + " " + String.join(" ", startCmd).replace("\t", " ") + "\n";

            FileUtils.writeStringToFile(Path.of(tmpScriptFolder.getAbsolutePath(), scriptName).toFile(),
                content, StandardCharsets.UTF_8);
            return scriptName;
        } catch (IOException ioe) {
            throw new TigerTestEnvException(
                "Failed to configure start script on container for server " + server.getServerId(), ioe);
        }

    }

    private String getContainerWorkingDirectory(ContainerConfig containerConfig) {
        if (containerConfig.getWorkingDir() == null || containerConfig.getWorkingDir().isBlank()) {
            return "";
        } else {
            return "cd " + containerConfig.getWorkingDir() + "\n";
        }
    }

    private void waitForHealthyStartup(TigerServer server, GenericContainer<?> container) {
        final long startms = System.currentTimeMillis();
        long endhalfms = server.getStartupTimeoutSec()
            .map(seconds -> seconds * 500L)
            .orElse(5000L);
        try {
            Thread.sleep(endhalfms);
        } catch (final InterruptedException e) {
            log.warn("Interrupted while waiting for startup of server " + server.getServerId(), e);
            Thread.currentThread().interrupt();
        }
        try {
            while (!container.isHealthy()) {
                //noinspection BusyWait
                Thread.sleep(500);
                if (startms + endhalfms * 2L < System.currentTimeMillis()) {
                    throw new TigerTestEnvException("Startup of server %s timed out after %d seconds!",
                        server.getServerId(), (System.currentTimeMillis() - startms) / 1000);
                }
            }
            log.info("HealthCheck OK ({}) for {}", (container.isHealthy() ? 1 : 0), server.getServerId());
        } catch (InterruptedException ie) {
            log.warn("Interruption signaled while waiting for server " + server.getServerId() + " to start up", ie);
            Thread.currentThread().interrupt();
        } catch (TigerTestEnvException ttee) {
            throw ttee;
        } catch (final RuntimeException rte) {
            int timeout = server.getStartupTimeoutSec().orElse(TigerServer.DEFAULT_STARTUP_TIMEOUT_IN_SECONDS);
            log.warn("probably no health check configured - defaulting to {}s startup time", timeout);
            try {
                Thread.sleep(timeout * 1000L);
            } catch (InterruptedException interruptedException) {
                log.warn("Interruption signaled");
                Thread.currentThread().interrupt();
            }
            log.warn("HealthCheck UNCLEAR for {} as no healthcheck is configured, we assume it works and continue setup!",
                server.getServerId());
        }
    }

    public void stopContainer(final TigerServer server) {
        final GenericContainer<?> container = containers.get(server.getServerId());
        if (container != null && container.getDockerClient() != null) {
            try {
                container.getDockerClient().stopContainerCmd(container.getContainerId()).exec();
            } catch (NotModifiedException nmex) {
                log.warn("Failed to issue stop container cmd from docker client, trying test container's stop...");
            }
            container.stop();
        }
    }

    @SuppressWarnings("unused")
    public void pauseContainer(final DockerServer srv) {
        final GenericContainer<?> container = containers.get(srv.getServerId());
        container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec();
    }

    @SuppressWarnings("unused")
    public void unpauseContainer(final DockerServer srv) {
        final GenericContainer<?> container = containers.get(srv.getServerId());
        container.getDockerClient().unpauseContainerCmd(container.getContainerId()).exec();
    }
}

