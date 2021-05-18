/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.PullResponseItem;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Slf4j
public class DockerMgr {

    @SuppressWarnings("OctalInteger")
    private static final int MOD_ALL_EXEC = 0777;

    Map<String, GenericContainer<?>> containers = new HashMap<>();

    @SuppressWarnings("unused")
    public void startContainer(final CfgServer server, final TigerTestEnvMgr envmgr) {
        var imageName = server.getInstanceUri().substring("docker:".length());
        if (server.getVersion() != null) {
            imageName += ":" + server.getVersion();
        }
        var imgName = DockerImageName.parse(imageName);

        pullImage(imageName);

        final GenericContainer<?> container = new GenericContainer<>(imgName);
        try {
            InspectImageResponse iiResponse = container.getDockerClient().inspectImageCmd(imageName).exec();
            if (iiResponse.getConfig() == null) {
                throw new TigerTestEnvException("Docker image '" + imageName + "' has no configuration info!");
            }
            String[] startCmd = iiResponse.getConfig().getCmd();
            String[] entryPointCmd = iiResponse.getConfig().getEntrypoint();

            // erezept hardcoded
            if (entryPointCmd != null && entryPointCmd[0].equals("/bin/sh") && entryPointCmd[1].equals("-c")) {
                entryPointCmd = new String[]{"su", iiResponse.getConfig().getUser(), "-c",
                    "'" + entryPointCmd[2] + "'"};
            }

            File tmpScriptFolder = Path.of("target", "tiger-testenv-mgr").toFile();
            final String scriptName = createContainerStartupScript(server, iiResponse, startCmd, entryPointCmd);
            String containerScriptPath = iiResponse.getConfig().getWorkingDir() + "/" + scriptName;
            container.withCopyFileToContainer(
                MountableFile.forHostPath(Path.of(tmpScriptFolder.getAbsolutePath(), scriptName), MOD_ALL_EXEC), containerScriptPath);

            container.withCreateContainerCmdModifier(
                cmd -> cmd.withUser("root").withEntrypoint(containerScriptPath));

            container.setLogConsumers(List.of(new Slf4jLogConsumer((log))));
            log.info("Passing in environment:");
            server.getImports().stream()
                .map(i -> i.split("=", 2))
                .forEach(envvar -> {
                    log.info("  " + envvar[0] + "=" + envvar[1]);
                    container.addEnv(envvar[0], envvar[1]);
                });
            container.start();
            // make startup time and intervall and url (supporting ${PORT} and regex content configurable
            try {
                Thread.sleep(5000);
            } catch (final InterruptedException e) {
                log.warn("Interrupted while waiting for startup of server " + server.getName());
            }
            waitForHealthyStartup(server, container);
            container.getDockerClient().renameContainerCmd(container.getContainerId())
                .withName("tiger." + server.getName()).exec();
            containers.put(server.getName(), container);
            final Map<Integer, Integer> ports = new HashMap<>();
            // TODO for now we assume ports are bound only to one other port on the docker container
            container.getContainerInfo().getNetworkSettings().getPorts().getBindings()
                .forEach((key, value) -> ports.put(key.getPort(), Integer.valueOf(value[0].getHostPortSpec())));
            server.setPorts(ports);


        } catch (final DockerException de) {
            throw new TigerTestEnvException("Failed to start container for server " + server.getName(), de);
        }

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
                    log.debug(item.getStatus() + " " + (item.getProgress() != null ? item.getProgress() : ""));
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
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        log.info("Docker image " + imageName + " is available locally!");
    }

    private String createContainerStartupScript(CfgServer server, InspectImageResponse iiResponse, String[] startCmd,
        String[] entryPointCmd) {
        if (iiResponse.getConfig() == null) {
            throw new TigerTestEnvException(
                "Docker image of server '" + server.getName() + "' has no configuration info!");
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
            tmpScriptFolder.mkdirs();
            final var scriptName = "__tigerStart_" + server.getName() + ".sh";
            FileUtils.writeStringToFile(Path.of(tmpScriptFolder.getAbsolutePath(), scriptName).toFile(),
                "#!/bin/sh -x\nenv\n"
                    // append proxy and other certs (for rise idp)
                    + "echo \"" + proxycert + "\" >> /etc/ssl/certs/ca-certificates.crt\n"
                    + "echo \"" + lecert + "\" >> /etc/ssl/certs/ca-certificates.crt\n"
                    + "echo \"" + risecert + "\" >> /etc/ssl/certs/ca-certificates.crt\n"

                    // testing ca cert of proxy with openssl
                    //+ "echo \"" + proxycert + "\" > /tmp/chain.pem\n"
                    //+ "openssl s_client -connect localhost:7000 -showcerts --proxy host.docker.internal:"
                    //+ envmgr.getLocalDockerProxy().getPort() + " -CAfile /tmp/chain.pem\n"
                    // idp-test.zentral.idp.splitdns.ti-dienste.de:443
                    // testing ca cert of proxy with rust client
                    //+ "RUST_LOG=trace /usr/bin/webclient https://idp-test.zentral.idp.splitdns.ti-dienste.de/.well-known/openid-configuration \n"

                    // change to working dir and execute former entrypoint/startcmd
                    + "cd " + iiResponse.getConfig().getWorkingDir() + "\n"
                    + String.join(" ", entryPointCmd).replace("\t", " ")
                    + " " + String.join(" ", startCmd).replace("\t", " ") + "\n",
                StandardCharsets.UTF_8
            );
            return scriptName;
        } catch (IOException e) {
            throw new TigerTestEnvException(
                "Failed to configure start script on container for server " + server.getName());
        }

    }

    private void waitForHealthyStartup(CfgServer server, GenericContainer<?> container) {
        final long startms = System.currentTimeMillis();
        try {
            while (!container.isHealthy()) {
                //noinspection BusyWait
                Thread.sleep(1000);
                if (server.getStartupTimeoutSec() != null && startms + server.getStartupTimeoutSec() * 1000 < System
                    .currentTimeMillis()) {
                    throw new TigerTestEnvException("Startup of server %s timed out after %d seconds!",
                        server.getName(), server.getStartupTimeoutSec());
                }
            }
            log.info("HealthCheck OK for " + server.getName());
        } catch (InterruptedException ie) {
            throw new TigerTestEnvException(
                "Interruption signaled while waiting for server " + server.getName() + " to start up", ie);
        } catch (TigerTestEnvException ttee) {
            throw ttee;
        } catch (final RuntimeException rte) {
            int timeout = server.getStartupTimeoutSec() != null ? server.getStartupTimeoutSec() : 20;
            log.warn("probably no health check configured - defaulting to " + timeout + "s startup time");
            try {
                Thread.sleep(timeout * 1000L);
            } catch (InterruptedException interruptedException) {
                interruptedException.printStackTrace();
            }
            log.info("HealthCheck UNCLEAR for " + server.getName()
                + " as no healtcheck is configured, we assume it works and continue setup!");
        }
    }

    public void stopContainer(final CfgServer srv) {
        final GenericContainer<?> container = containers.get(srv.getName());
        if (container != null && container.getDockerClient() != null) {
            container.getDockerClient().stopContainerCmd(container.getContainerId()).exec();
            container.stop();
        }
    }

    @SuppressWarnings("unused")
    public void pauseContainer(final CfgServer srv) {
        final GenericContainer<?> container = containers.get(srv.getName());
        container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec();
    }

    @SuppressWarnings("unused")
    public void unpauseContainer(final CfgServer srv) {
        final GenericContainer<?> container = containers.get(srv.getName());
        container.getDockerClient().unpauseContainerCmd(container.getContainerId()).exec();
    }
}

