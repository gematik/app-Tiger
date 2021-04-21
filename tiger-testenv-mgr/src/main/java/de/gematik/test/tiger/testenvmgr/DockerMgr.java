package de.gematik.test.tiger.testenvmgr;

import com.github.dockerjava.api.exception.DockerException;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import java.util.HashMap;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.utility.DockerImageName;

public class DockerMgr {

    Map<String, GenericContainer<?>> containers = new HashMap<>();

    public DockerMgr() {
    }

    public void startContainer(final CfgServer server) {
        try {
            String imageName = server.getInstanceUri().substring("docker:" .length());
            if (server.getVersion() != null) {
                imageName += ":" + server.getVersion();
            }
            final GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(imageName));
            container.start();
            container.waitingFor(new HostPortWaitStrategy());
            container.getDockerClient().renameContainerCmd(container.getContainerId())
                .withName("tiger." + server.getName()).exec();
            containers.put(server.getName(), container);
            final Map<Integer, Integer> ports = new HashMap<>();
            // TODO for now we assume ports are bound only to one other port on the docker container
            container.getContainerInfo().getNetworkSettings().getPorts().getBindings()
                .forEach((key, value) -> ports.put(key.getPort(), Integer.valueOf(value[0].getHostPortSpec())));
            server.setPorts(ports);
        } catch (final DockerException de) {
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

