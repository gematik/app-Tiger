/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.ResponseItem;
import de.gematik.test.tiger.common.OsEnvironment;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.servers.DockerComposeServer;
import de.gematik.test.tiger.testenvmgr.servers.DockerServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
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
                                + server.getHostname());
                    }
                }
                final String scriptName = createContainerStartupScript(server, iiResponse, startCmd, entryPointCmd);
                String containerScriptPath = containerConfig.getWorkingDir() + "/" + scriptName;
                container.withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of(tmpScriptFolder.getAbsolutePath(), scriptName), MOD_ALL_EXEC),
                    containerScriptPath);
                container.withCreateContainerCmdModifier(
                    cmd -> cmd.withUser("root").withEntrypoint(containerScriptPath));
            }

            container.setLogConsumers(List.of(new Slf4jLogConsumer(log)));
            log.info("Passing in environment:");
            addEnvVarsToContainer(container, server.getEnvironmentProperties());

            if (server.getDockerOptions().isOneShot()) {
                container.withStartupCheckStrategy(new OneShotStartupCheckStrategy());
            }
            container.start();
            // make startup time and intervall and url (supporting ${PORT} and regex content configurable
            waitForHealthyStartup(server, container);
            container.getDockerClient().renameContainerCmd(container.getContainerId())
                .withName("tiger." + server.getHostname()).exec();
            containers.put(server.getHostname(), container);
            final Map<Integer, Integer> ports = new HashMap<>();
            // TODO TGR-282 LO PRIO for now we assume ports are bound only to one other port on the docker container
            // maybe just make clear we support only single exported port
            container.getContainerInfo().getNetworkSettings().getPorts().getBindings().entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .forEach(entry -> ports.put(entry.getKey().getPort(),
                    Integer.valueOf(entry.getValue()[0].getHostPortSpec())));
            server.getDockerOptions().setPorts(ports);
        } catch (final DockerException de) {
            throw new TigerTestEnvException("Failed to start container for server " + server.getHostname(), de);
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
        var folder = Paths.get("target", "tiger-testenv-mgr", server.getHostname()).toFile();
        if (!folder.exists() && !folder.mkdirs()) {
            throw new TigerTestEnvException("Unable to create temp folder " + folder.getAbsolutePath());
        }
        File[] composeFiles = server.getSource().stream().map(f -> {
            if (f.startsWith(CLZPATH)) {
                var tmpFile = Paths.get(folder.getAbsolutePath(), f.substring(CLZPATH.length())).toFile();
                InputStream is = getClass().getResourceAsStream(f.substring(CLZPATH.length()));
                if (is == null) {
                    throw new TigerTestEnvException("Missing docker compose file in classpath " + f);
                }
                if (!tmpFile.getParentFile().exists() && !tmpFile.getParentFile().mkdirs()) {
                    throw new TigerTestEnvException(
                        "Unable to create temp folder " + tmpFile.getParentFile().getAbsolutePath());
                }
                try (var fos = new FileOutputStream(tmpFile)) {
                    IOUtils.copy(is, fos);
                    f = tmpFile.getAbsolutePath();
                } catch (IOException ioe) {
                    throw new TigerTestEnvException("Unable to create temp docker compose files (" + f + ")", ioe);
                }
            }
            return new File(f);
        }).toArray(File[]::new);
        DockerComposeContainer composition = new DockerComposeContainer(composeFiles) //NOSONAR
            .withLogConsumer("epa-gateway", new Slf4jLogConsumer((log)));
        try {
            for (String check : server.getDockerOptions().getServiceHealthchecks()) {
                try {
                    URL serviceUrl = new URL(check);
                    WaitStrategy waitStrategy = (serviceUrl.getProtocol().equals("http") ?
                        Wait.forHttp(serviceUrl.getPath()) : Wait.forHttps(serviceUrl.getPath())).forStatusCode(200)
                        .forStatusCode(404)
                        .withStartupTimeout(Duration.of(server.getStartupTimeoutSec()
                                .orElse(TigerServer.DEFAULT_STARTUP_TIMEOUT_IN_SECONDS),
                            ChronoUnit.SECONDS));
                    composition = composition.withExposedService(
                        serviceUrl.getHost(), serviceUrl.getPort(), waitStrategy);
                } catch (MalformedURLException e) {
                    throw new TigerTestEnvException(
                        "Invalid health check URL '" + check + "' for server " + server.getHostname());
                }
            }
        } catch (Exception e) {
            throw new TigerTestEnvException("Unable to start server " + server.getHostname());
        }
        composition.start(); //NOSONAR
    }

    private void addEnvVarsToContainer(GenericContainer<?> container, List<String> envVars) {
        envVars.stream()
            .filter(i -> i.contains("="))
            .map(i -> i.split("=", 2))
            .peek(envvar -> log.info("  * " + envvar[0] + "=" + envvar[1]))
            .forEach(envvar -> container.addEnv(
                TigerGlobalConfiguration.resolvePlaceholders(envvar[0]),
                TigerGlobalConfiguration.resolvePlaceholders(envvar[1])));
    }

    public void pullImage(final String imageName) {
        log.info("Pulling docker image " + imageName + "...");
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

        while (!pullComplete.get()) {
            if (cbException.get() != null) {
                throw new TigerTestEnvException("Unable to pull image " + imageName + "!", cbException.get());
            }
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                log.warn("Interruption signaled", e);
                Thread.currentThread().interrupt();
            }
        }
        log.info("Docker image " + imageName + " is available locally!");
    }

    private String createContainerStartupScript(TigerServer server, InspectImageResponse iiResponse, String[] startCmd,
        String[] entryPointCmd) {
        final ContainerConfig containerConfig = iiResponse.getConfig();
        if (containerConfig == null) {
            throw new TigerTestEnvException(
                "Docker image of server '" + server.getHostname() + "' has no configuration info!");
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
            final var scriptName = "__tigerStart_" + server.getHostname() + ".sh";
            var content = "#!/bin/sh -x\nenv\n"
                // append proxy and other certs (for rise idp)
                + "echo \"" + proxycert + "\" >> /etc/ssl/certs/ca-certificates.crt\n"
                + "echo \"" + lecert + "\" >> /etc/ssl/certs/ca-certificates.crt\n"
                + "echo \"" + risecert + "\" >> /etc/ssl/certs/ca-certificates.crt\n";

            // workaround for host.docker.internal not being available on linux based docker
            // see https://github.com/docker/for-linux/issues/264
            if (SystemUtils.IS_OS_LINUX || SystemUtils.IS_OS_MAC) {
                String hostip = OsEnvironment.getDockerHostIp();
                log.info("patching /etc/hosts for possibly non supported symbolic host.docker.internal");
                content += "grep -q \"host.docker.internal\" /etc/hosts || "
                    + "echo \" \" >> /etc/hosts && echo \"" + hostip + "    host.docker.internal\" >> /etc/hosts\n";

                // TODO TGR-283 reactivate once we have docker v20 on maven nodes
                //  container.withExtraHost("host.docker.internal", "host-gateway");
                content += "echo HOSTS:\ncat /etc/hosts\n";
            } else {
                log.info("skipping etc hosts patch...");
            }

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
                "Failed to configure start script on container for server " + server.getHostname(), ioe);
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
            .orElse(5000l);
        try {
            Thread.sleep(endhalfms);
        } catch (final InterruptedException e) {
            log.warn("Interrupted while waiting for startup of server " + server.getHostname(), e);
            Thread.currentThread().interrupt();
        }
        try {
            while (!container.isHealthy()) {
                //noinspection BusyWait
                Thread.sleep(500);
                if (startms + endhalfms * 2L < System.currentTimeMillis()) {
                    throw new TigerTestEnvException("Startup of server %s timed out after %d seconds!",
                        server.getHostname(), (System.currentTimeMillis() - startms) / 1000);
                }
            }
            log.info("HealthCheck OK (" + (container.isHealthy() ? 1 : 0) + ") for " + server.getHostname());
        } catch (InterruptedException ie) {
            log.warn("Interruption signaled while waiting for server " + server.getHostname() + " to start up", ie);
            Thread.currentThread().interrupt();
        } catch (TigerTestEnvException ttee) {
            throw ttee;
        } catch (final RuntimeException rte) {
            int timeout = server.getStartupTimeoutSec().orElse(TigerServer.DEFAULT_STARTUP_TIMEOUT_IN_SECONDS);
            log.warn("probably no health check configured - defaulting to " + timeout + "s startup time");
            try {
                Thread.sleep(timeout * 1000L);
            } catch (InterruptedException interruptedException) {
                log.warn("Interruption signaled");
                Thread.currentThread().interrupt();
            }
            log.warn("HealthCheck UNCLEAR for " + server.getHostname()
                + " as no healthcheck is configured, we assume it works and continue setup!");
        }
    }

    public void stopContainer(final TigerServer server) {
        final GenericContainer<?> container = containers.get(server.getHostname());
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
        final GenericContainer<?> container = containers.get(srv.getHostname());
        container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec();
    }

    @SuppressWarnings("unused")
    public void unpauseContainer(final DockerServer srv) {
        final GenericContainer<?> container = containers.get(srv.getHostname());
        container.getDockerClient().unpauseContainerCmd(container.getContainerId()).exec();
    }
}

