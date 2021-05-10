package de.gematik.test.tiger.testenvmgr;

import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.DockerException;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.utility.TestcontainersConfiguration;

@Slf4j
public class DockerMgr {

    Map<String, GenericContainer<?>> containers = new HashMap<>();

    public DockerMgr() {
    }

    public void startContainer(final CfgServer server, final TigerTestEnvMgr envmgr) {
        try {
            String imageName = server.getInstanceUri().substring("docker:".length());
            if (server.getVersion() != null) {
                imageName += ":" + server.getVersion();
            }
            TestcontainersConfiguration cfg = TestcontainersConfiguration.getInstance();
            DockerImageName imgName = DockerImageName.parse(imageName);
            final GenericContainer<?> container = new GenericContainer<>(imgName);
                //.withImagePullPolicy(PullPolicy.alwaysPull());
            InspectImageResponse iiResponse = container.getDockerClient().inspectImageCmd(imageName).exec();

            String[] startCmd = iiResponse.getConfig().getCmd();
            String[] entryPointCmd = iiResponse.getConfig().getEntrypoint();

            // erezept hardcoded
            if (entryPointCmd != null && entryPointCmd[0].equals("/bin/sh") && entryPointCmd[1].equals("-c")) {
                entryPointCmd = new String[]{"su", iiResponse.getConfig().getUser(), "-c",
                    "'" + entryPointCmd[2] + "'"};
            }
            try {

                String proxycert = IOUtils.toString(
                    Objects.requireNonNull(getClass().getResourceAsStream("/CertificateAuthorityCertificate.pem")),
                    StandardCharsets.UTF_8);
                String lecert = IOUtils.toString(
                    Objects.requireNonNull(getClass().getResourceAsStream("/letsencrypt.crt")),
                    StandardCharsets.UTF_8);
                String risecert = IOUtils.toString(
                    Objects.requireNonNull(getClass().getResourceAsStream("/idp-rise-tu.crt")),
                    StandardCharsets.UTF_8);
                String scriptName = "__tigerStart_" + server.getName() + ".sh";
                FileUtils.writeStringToFile(Path.of(scriptName).toFile(),
                    "#!/bin/sh -x\nenv\n"
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

                        + "cd " + iiResponse.getConfig().getWorkingDir() + "\n"

                        + String.join(" ", Optional.ofNullable(entryPointCmd).orElse(new String[0])).replace("\t", " ")
                        + " "
                        + String.join(" ", Optional.ofNullable(startCmd).orElse(new String[0])) + "\n",
                    StandardCharsets.UTF_8
                );
                //);

                // testing ca cert of proxy with rust client
                //container.withCopyFileToContainer(MountableFile.forHostPath("webclient", 0777),
                //    "/usr/bin/webclient");

                String containerScriptPath = iiResponse.getConfig().getWorkingDir() + "/" + scriptName;
                container.withCopyFileToContainer(
                    MountableFile.forHostPath(scriptName, 0777),
                    containerScriptPath);

                container.withCreateContainerCmdModifier(
                    cmd -> cmd.withUser("root").withEntrypoint(containerScriptPath));
            } catch (IOException e) {
                throw new AssertionError(
                    "Failed to configure start script on container for server " + server.getName());
            }

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
                e.printStackTrace();
            }
            final boolean health = false;
            final long startms = System.currentTimeMillis();
            try {
                while (!container.isHealthy()) {
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
                    Thread.sleep(timeout * 1000);
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
                log.info("HealthCheck UNCLEAR for " + server.getName()
                    + " as no healtcheck is configured, we assume it works and continue setup!");
            }
            container.getDockerClient().renameContainerCmd(container.getContainerId())
                .withName("tiger." + server.getName()).exec();
            containers.put(server.getName(), container);
            final Map<Integer, Integer> ports = new HashMap<>();
            // TODO for now we assume ports are bound only to one other port on the docker container
            container.getContainerInfo().getNetworkSettings().getPorts().getBindings()
                .forEach((key, value) -> ports.put(key.getPort(), Integer.valueOf(value[0].getHostPortSpec())));
            server.setPorts(ports);


        } catch (
            final DockerException de) {
            throw new TigerTestEnvException("Failure while starting container for server " + server.getName(), de);
        }

    }

    public void stopContainer(final CfgServer srv) {
        final GenericContainer<?> container = containers.get(srv.getName());
        container.getDockerClient().stopContainerCmd(container.getContainerId()).exec();
        container.stop();
    }

    public void pauseContainer(final CfgServer srv) {
        final GenericContainer<?> container = containers.get(srv.getName());
        container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec();
    }

    public void unpauseContainer(final CfgServer srv) {
        final GenericContainer<?> container = containers.get(srv.getName());
        container.getDockerClient().unpauseContainerCmd(container.getContainerId()).exec();
    }
}

