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
            container.setEnv(server.getImports());
            container.start();
            // make startup time and intervall and url (supporting ${PORT} and regex content configurable
            try {
                Thread.sleep(12000);
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
            final boolean health = false;
            while (!container.isHealthy()) {
                try {
                    Thread.sleep(2000);
                } catch (final InterruptedException e) {
                }
            }
            log.warn("HealthCheck OK for " + server.getName());
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

