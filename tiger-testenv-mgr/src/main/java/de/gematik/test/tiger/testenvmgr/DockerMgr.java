package de.gematik.test.tiger.testenvmgr;

import com.github.dockerjava.api.exception.DockerException;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

@Slf4j
public class DockerMgr {

    Map<String, GenericContainer<?>> containers = new HashMap<>();

    public DockerMgr() {
    }

    public void startContainer(final CfgServer server) {
        try {
            String imageName = server.getInstanceUri().substring("docker:".length());
            if (server.getVersion() != null) {
                imageName += ":" + server.getVersion();
            }
            final GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(imageName));
            container.setLogConsumers(List.of(new Slf4jLogConsumer((log))));
            log.info("Passing in environment:");
            server.getImports().forEach(envvar -> log.info("  " + envvar));
            container.setEnv(server.getImports());
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
                throw new TigerTestEnvException("Interruption signaled while waiting for server " + server.getName() + " to start up", ie);
            } catch (TigerTestEnvException ttee) {
                throw ttee;
            } catch (final RuntimeException rte) {
                int timeout = server.getStartupTimeoutSec() != null ?  server.getStartupTimeoutSec() : 20;
                log.warn("probably no health check configured - defaulting to " + timeout + "s startup time");
                try {
                    Thread.sleep(timeout*1000);
                } catch (InterruptedException interruptedException) {
                    interruptedException.printStackTrace();
                }
                log.info("HealthCheck UNCLEAR for " + server.getName() + " as no healtcheck is configured, we assume it works and continue setup!");
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

