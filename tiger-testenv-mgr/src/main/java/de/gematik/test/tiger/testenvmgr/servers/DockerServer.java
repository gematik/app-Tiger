/*
 *
 * Copyright 2021-2026 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */
package de.gematik.test.tiger.testenvmgr.servers;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.config.DockerServerConfiguration;
import de.gematik.test.tiger.testenvmgr.events.BeforeContainerStartEvent;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Minimal {@code docker} server type. Wraps Testcontainers' {@link GenericContainer} so users can
 * run arbitrary images from a {@code tiger.yaml} without writing docker-compose. The typed
 * configuration is read via the {@code typeSpecificConfig} SPI ({@code
 * AbstractTigerServer#readTypeSpecificConfig}), deliberately exercising that extension hook from an
 * in-tree consumer.
 *
 * <p>This server type is the in-tree producer of {@link BeforeContainerStartEvent}. The canopy
 * auto-DNS injector subscribes to that event and mutates the carrier's DNS list pre-{@code start};
 * this server applies the carrier's final state to the container builder afterwards. No coupling
 * either way — both sides talk to the SPI.
 *
 * <p>Scope is deliberately narrow: single container, no compose, no orchestration. Everything
 * beyond that is a follow-up.
 */
@TigerServerType("docker")
public class DockerServer extends AbstractTigerServer {

  private static final String CONFIG_KEY = "docker";

  private DockerServerConfiguration dockerConfig;
  private GenericContainer<?> container;

  public DockerServer(String serverId, CfgServer configuration, TigerTestEnvMgr tigerTestEnvMgr) {
    super(serverId, configuration, tigerTestEnvMgr);
  }

  /**
   * Visible-for-test accessor to the underlying Testcontainers handle. {@code null} until {@link
   * #performStartup()} has run. Used by ITs to run {@code execInContainer(...)} against the running
   * target.
   */
  public GenericContainer<?> getDockerContainer() {
    return container;
  }

  @Override
  public void assertThatConfigurationIsCorrect() {
    super.assertThatConfigurationIsCorrect();
    if (dockerConfig == null) {
      dockerConfig = readTypeSpecificConfig(CONFIG_KEY, DockerServerConfiguration.class);
    }
    if (dockerConfig == null) {
      throw new TigerEnvironmentStartupException(
          "Server '%s' of type 'docker' is missing its 'docker:' configuration block.",
          getServerId());
    }
    if (StringUtils.isBlank(dockerConfig.getImage())) {
      throw new TigerEnvironmentStartupException(
          "Server '%s' of type 'docker' is missing required field 'docker.image'.", getServerId());
    }
    // Multi-network is a v1 limitation; fail fast at config time.
    if (dockerConfig.getNetworks().size() > 1) {
      throw new TigerEnvironmentStartupException(
          "Server '%s': multi-network attach is not supported in v1 of the docker server type "
              + "(got %s). Use a single network; multi-network support is a follow-up.",
          getServerId(), dockerConfig.getNetworks());
    }
    // PORTS_LISTENING without exposed ports is a contradiction.
    DockerServerConfiguration.WaitStrategyConfig ws = dockerConfig.getWaitStrategy();
    if (ws.getKind() == DockerServerConfiguration.WaitStrategyConfig.Kind.PORTS_LISTENING
        && dockerConfig.getExposedPorts().isEmpty()) {
      throw new TigerEnvironmentStartupException(
          "Server '%s': waitStrategy.kind=PORTS_LISTENING requires at least one entry in "
              + "docker.exposedPorts. Use kind=NONE for fire-and-forget workloads.",
          getServerId());
    }
    if (ws.getKind() == DockerServerConfiguration.WaitStrategyConfig.Kind.LOG
        && StringUtils.isBlank(ws.getLogPattern())) {
      throw new TigerEnvironmentStartupException(
          "Server '%s': waitStrategy.kind=LOG requires waitStrategy.logPattern.", getServerId());
    }
  }

  /**
   * Pre-validation hook. Injects an implicit {@code dependsUpon <canopyId>} edge when:
   *
   * <ul>
   *   <li>the docker block is present and well-formed,
   *   <li>the user has not opted out via {@code docker.injectDns: false},
   *   <li>the user has not provided explicit {@code docker.dnsServers} (in which case auto-DNS
   *       won't fire anyway, so the edge would just slow startup),
   *   <li>and there is exactly one canopy server in the env (matches the auto-DNS injector's
   *       multi-canopy stance).
   * </ul>
   *
   * This pairs with the canopy auto-DNS injector ({@code
   * de.gematik.test.tiger.canopy.extension.CanopyDnsAutoInjector} in the {@code
   * tiger-canopy-extension} module): with the edge in place, the canopy is guaranteed to be RUNNING
   * by the time the docker target fires its {@code BeforeContainerStartEvent}, so the injector can
   * read the canopy's container host address synchronously instead of having to skip with a
   * warning.
   */
  @Override
  protected void doPrepareDependencies() {
    if (dockerConfig == null) {
      dockerConfig = readTypeSpecificConfig(CONFIG_KEY, DockerServerConfiguration.class);
    }
    if (dockerConfig == null) {
      return; // malformed config — let assertThatConfigurationIsCorrect report it.
    }
    if (!dockerConfig.isInjectDns()) {
      return;
    }
    if (!dockerConfig.getDnsServers().isEmpty()) {
      return; // user has explicit DNS; auto-DNS won't fire.
    }
    // Late-bound by class name to keep tiger-testenv-mgr core free of a hard compile-time
    // edge into the canopy adapter package (the ArchUnit boundary test enforces this).
    var canopies = findUniqueCanopyServerId();
    canopies.ifPresent(id -> getConfiguration().addDependsUpon(id));
  }

  private java.util.Optional<String> findUniqueCanopyServerId() {
    // Use TigerServerType token instead of a hard class reference so the canopy package is
    // not on the import surface of DockerServer (preserves the ArchUnit boundary carve-out).
    var candidates =
        getTigerTestEnvMgr().getServers().values().stream()
            .filter(s -> s != this && "canopy".equals(s.getConfiguration().getType()))
            .toList();
    if (candidates.size() != 1) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(candidates.get(0).getServerId());
  }

  @Override
  public void performStartup() {
    statusMessage(
        "Building Docker container '" + getServerId() + "' from image " + dockerConfig.getImage());

    container = new GenericContainer<>(DockerImageName.parse(dockerConfig.getImage()));

    // Initial state seeded from typed config; subscribers may mutate it pre-start.
    BeforeContainerStartEvent event =
        new BeforeContainerStartEvent(
            this,
            new ArrayList<>(dockerConfig.getDnsServers()),
            new ArrayList<>(dockerConfig.getNetworks()),
            new LinkedHashMap<>(dockerConfig.getEnv()));
    getTigerTestEnvMgr().getLifecycleEventBus().publish(event);

    // Apply the carrier's (possibly mutated) state to the container builder.
    applyCarrierToContainer(event);

    if (!dockerConfig.getExposedPorts().isEmpty()) {
      container.withExposedPorts(dockerConfig.getExposedPorts().toArray(Integer[]::new));
    }
    if (!dockerConfig.getCommand().isEmpty()) {
      container.withCommand(dockerConfig.getCommand().toArray(String[]::new));
    }
    applyPortMappings(container, dockerConfig);
    applyWaitStrategy(container, dockerConfig);

    statusMessage("Starting Docker container '" + getServerId() + "'");
    container.start();

    publishPlaceholders();
    statusMessage(
        "Docker container '" + getServerId() + "' running with id " + container.getContainerId());
  }

  /** Applies the post-event carrier state to the underlying container builder. */
  private void applyCarrierToContainer(BeforeContainerStartEvent event) {
    if (!event.getDnsServers().isEmpty()) {
      List<String> dns = event.getDnsServers();
      Consumer<CreateContainerCmd> dnsModifier = cmd -> cmd.getHostConfig().withDns(dns);
      container.withCreateContainerCmdModifier(dnsModifier);
    }
    event.getExtraEnv().forEach(container::withEnv);
    List<String> nets = event.getNetworks();
    if (nets.size() > 1) {
      throw new TigerEnvironmentStartupException(
          "Server '%s': multi-network attach is not supported in v1 of the docker server type "
              + "(got %s). Use a single network; multi-network support is a follow-up.",
          getServerId(), nets);
    }
    if (!nets.isEmpty()) {
      container.withNetworkMode(nets.get(0));
    }
  }

  private void applyPortMappings(GenericContainer<?> container, DockerServerConfiguration cfg) {
    if (cfg.getPortMappings().isEmpty()) {
      return;
    }
    Consumer<CreateContainerCmd> binder =
        cmd -> {
          List<PortBinding> bindings = new ArrayList<>();
          cfg.getPortMappings()
              .forEach(
                  (containerPort, hostPort) ->
                      bindings.add(PortBinding.parse(hostPort + ":" + containerPort)));
          cmd.getHostConfig().withPortBindings(bindings);
          // Make sure each mapped container port is also exposed.
          for (Integer containerPort : cfg.getPortMappings().keySet()) {
            cmd.withExposedPorts(ExposedPort.tcp(containerPort));
          }
        };
    container.withCreateContainerCmdModifier(binder);
  }

  private void applyWaitStrategy(GenericContainer<?> container, DockerServerConfiguration cfg) {
    DockerServerConfiguration.WaitStrategyConfig ws = cfg.getWaitStrategy();
    Duration timeout = Duration.ofSeconds(Math.max(1, ws.getTimeoutSeconds()));
    switch (ws.getKind()) {
      case PORTS_LISTENING -> {
        if (cfg.getExposedPorts().isEmpty()) {
          throw new TigerEnvironmentStartupException(
              "Server '%s': waitStrategy.kind=PORTS_LISTENING requires at least one entry in "
                  + "docker.exposedPorts. Use kind=NONE for fire-and-forget workloads.",
              getServerId());
        }
        container.waitingFor(Wait.forListeningPort().withStartupTimeout(timeout));
      }
      case HTTP -> {
        int port =
            ws.getHttpPort() > 0
                ? ws.getHttpPort()
                : cfg.getExposedPorts().stream()
                    .findFirst()
                    .orElseThrow(
                        () ->
                            new TigerEnvironmentStartupException(
                                "Server '%s': waitStrategy.kind=HTTP requires either waitStrategy.httpPort "
                                    + "or at least one entry in docker.exposedPorts.",
                                getServerId()));
        container.waitingFor(
            Wait.forHttp(StringUtils.defaultIfBlank(ws.getHttpPath(), "/"))
                .forPort(port)
                .forStatusCode(ws.getHttpStatus())
                .withStartupTimeout(timeout));
      }
      case LOG -> {
        if (StringUtils.isBlank(ws.getLogPattern())) {
          throw new TigerEnvironmentStartupException(
              "Server '%s': waitStrategy.kind=LOG requires waitStrategy.logPattern.",
              getServerId());
        }
        container.waitingFor(Wait.forLogMessage(ws.getLogPattern(), 1).withStartupTimeout(timeout));
      }
      case NONE -> {
        // Intentionally empty: rely on Testcontainers' default container-started probe.
      }
    }
  }

  /**
   * Publishes per-server runtime placeholders so other servers (and the user's tests) can reference
   * them via {@code ${<id>.X}}. Lives in the bare top-level namespace deliberately, matching the
   * placeholder syntax used elsewhere (e.g. {@code ${canopy.dnsAddress}}). Server ids must be
   * unique among themselves and pass the {@code [a-zA-Z0-9-]+} hostname regex, so collisions with
   * arbitrary user-defined top-level keys are a self-inflicted wound and easy to spot.
   *
   * <p>Avoids the {@code tiger.servers.<id>.*} namespace which is reserved for the server's static
   * YAML configuration (see {@code DeprecatedKeysUsageChecker} et al.).
   */
  private void publishPlaceholders() {
    String prefix = getServerId() + ".";
    TigerGlobalConfiguration.putValue(prefix + "host", container.getHost());
    TigerGlobalConfiguration.putValue(prefix + "containerId", container.getContainerId());
    Map<Integer, Integer> mappings = collectPortMappings();
    mappings.forEach(
        (containerPort, hostPort) ->
            TigerGlobalConfiguration.putValue(
                prefix + containerPort + ".hostPort", Integer.toString(hostPort)));
  }

  private Map<Integer, Integer> collectPortMappings() {
    Map<Integer, Integer> result = new LinkedHashMap<>(dockerConfig.getPortMappings());
    for (Integer exposed : dockerConfig.getExposedPorts()) {
      result.putIfAbsent(exposed, container.getMappedPort(exposed));
    }
    return result;
  }

  @Override
  public void shutdown() {
    if (container != null && container.isRunning()) {
      statusMessage("Stopping Docker container '" + getServerId() + "'");
      container.stop();
    }
    setStatus(TigerServerStatus.STOPPED, "Docker server " + getServerId() + " stopped");
  }

  /** Visible-for-test accessor. */
  GenericContainer<?> getContainer() {
    return container;
  }

  /** Visible-for-test accessor. */
  public DockerServerConfiguration getDockerConfig() {
    return dockerConfig;
  }
}
