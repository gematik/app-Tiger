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
package de.gematik.test.tiger.canopy.extension;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import de.gematik.test.tiger.canopy.client.CanopyAdminClient;
import de.gematik.test.tiger.canopy.client.config.ControlMode;
import de.gematik.test.tiger.canopy.client.config.HttpVersion;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.servers.AbstractTigerServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerProxyServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerStatus;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerType;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Tiger {@link AbstractTigerServer} that boots a CANOPY DNS-rewriting service from a {@code
 * tiger.yaml} entry.
 *
 * <p>This class lives in the {@code canopy.extension} package — the in-tree home of canopy-named
 * symbols, exempted from the ArchUnit boundary rule. When the canopy module gets extracted to its
 * own repository, this whole package moves with it.
 *
 * <p>YAML:
 *
 * <pre>{@code
 * servers:
 *   myCanopy:
 *     type: canopy
 *     canopy:
 *       image: de.gematik.test/tiger-canopy:1.0.0
 *       tigerProxyUrl: http://my-tiger-proxy:9090/
 *       proxiedHosts:
 *         - host: api.example.com           # matchType defaults to EXACT
 *         - host: example.org
 *           matchType: SUFFIX               # also catches *.example.org
 * }</pre>
 *
 * <p>Reads {@link TigerCanopyConfiguration} via the {@code typeSpecificConfig} typed-config slot
 * (no canopy-specific symbols on {@code CfgServer}). Forwards the runtime fields into the container
 * as a {@code SPRING_APPLICATION_JSON} env var so the canopy Spring context binds them on boot.
 *
 * <p>Publishes placeholders {@code <id>.baseUrl}, {@code <id>.dnsAddress}, {@code <id>.dnsPort}.
 * Use {@link #getAdminClient()} to interact with the running canopy programmatically.
 */
@TigerServerType("canopy")
public class CanopyServer extends AbstractTigerServer {

  /** Image used when the user does not override it via {@code canopy.image}. */
  static final String DEFAULT_IMAGE = "de.gematik.test/tiger-canopy:latest";

  private static final String CONFIG_KEY = "canopy";
  private static final ObjectMapper SAJ_MAPPER =
      new ObjectMapper().setSerializationInclusion(Include.NON_NULL);

  private TigerCanopyConfiguration canopyConfig;
  private GenericContainer<?> container;
  private CanopyAdminClient adminClient;

  public CanopyServer(String serverId, CfgServer configuration, TigerTestEnvMgr tigerTestEnvMgr) {
    super(serverId, configuration, tigerTestEnvMgr);
  }

  /**
   * Pure validation of an <em>already-initialised</em> {@link #canopyConfig}. Runs per server
   * inside {@code start()} after {@link #doPrepareDependencies()} has set the field. Performs no
   * typed-config reads, no defaulting, no field mutation — initialisation is owned exclusively by
   * {@code doPrepareDependencies}. Throws {@link TigerEnvironmentStartupException} when invoked
   * before {@code prepareDependencies()} or when {@code adminPort}/{@code dnsHostPort} are out of
   * the [0, 65535] TCP/UDP range.
   */
  @Override
  public void assertThatConfigurationIsCorrect() {
    super.assertThatConfigurationIsCorrect();
    if (canopyConfig == null) {
      throw new TigerEnvironmentStartupException(
          "Server '%s': canopy configuration not initialised — prepareDependencies() must run"
              + " before assertThatConfigurationIsCorrect().",
          getServerId());
    }
    int adminPort = canopyConfig.getAdminPort();
    if (adminPort < 0 || adminPort > 65_535) {
      throw new TigerEnvironmentStartupException(
          "Server '%s': canopy.adminPort=%d is out of range.", getServerId(), adminPort);
    }
    int dnsHostPort = canopyConfig.getDnsHostPort();
    if (dnsHostPort < 0 || dnsHostPort > 65_535) {
      throw new TigerEnvironmentStartupException(
          "Server '%s': canopy.dnsHostPort=%d is out of range.", getServerId(), dnsHostPort);
    }
  }

  /**
   * Pre-validation hook. Single owner of {@link #canopyConfig} initialisation: reads the typed
   * canopy block from {@code typeSpecificConfig} (so the dependency-graph build sees the same view
   * of the world), defaults to an empty config when absent, and auto-wires {@code tigerProxyUrl} to
   * a unique sibling {@code tigerProxy} server when the user did not set it explicitly. Mutating
   * {@code dependsUpon} here (rather than from {@code assertThatConfigurationIsCorrect}, which runs
   * inside {@code start()} per server) guarantees the implicit edge is visible to {@link
   * de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr}'s cycle/unknown-dependency checks and to the
   * initial-boot fan-out.
   */
  @Override
  protected void doPrepareDependencies() {
    if (canopyConfig == null) {
      canopyConfig = readTypeSpecificConfig(CONFIG_KEY, TigerCanopyConfiguration.class);
    }
    if (canopyConfig == null) {
      // Empty canopy block → use defaults. Mirrors Tiger's "minimal config wins" stance.
      canopyConfig = new TigerCanopyConfiguration();
    }
    autoWireTigerProxyUrl();
    autoWireDependsUponFromPlaceholders();
    // Register the auto-DNS injector now, BEFORE any container can start. That way the
    // BeforeContainerStartEvent subscriber is in place for every docker target — including
    // any that the user wired without an explicit dependsUpon (DockerServer adds its own
    // implicit edge in doPrepareDependencies, but the subscriber being registered here
    // covers the case where the user opted out of that edge while still wanting auto-DNS).
    new CanopyDnsAutoInjector(getTigerTestEnvMgr(), this).register();
  }

  /**
   * If the user did not set {@code canopy.tigerProxyUrl}, derive it from a sibling {@code
   * tigerProxy} server in the env. v1 only handles the unique-sibling case; multi-proxy
   * environments must wire explicitly.
   *
   * <p>When auto-wire fires we:
   *
   * <ul>
   *   <li>store the unresolved placeholder {@code ${<proxyId>.adminUrl}} on the canopy
   *       configuration — {@link de.gematik.test.tiger.testenvmgr.servers.TigerProxyServer}
   *       publishes this key with a {@code host.docker.internal} URL after the proxy boots, which
   *       the canopy container resolves at startup time. Storing the placeholder rather than the
   *       resolved URL avoids the chicken-and-egg of needing the proxy to be RUNNING while we are
   *       still building the dependency graph;
   *   <li>append the proxy id to {@code dependsUpon} so the proxy is guaranteed to be RUNNING (and
   *       the placeholder thus resolvable) by the time canopy starts;
   *   <li>default {@code controlMode} to {@code ROUTE_PER_HOST} (only if it was still {@code
   *       NONE}).
   * </ul>
   */
  private void autoWireTigerProxyUrl() {
    if (StringUtils.isNotBlank(canopyConfig.getTigerProxyUrl())) {
      return;
    }
    var proxies = getTigerTestEnvMgr().getServersOfType(TigerProxyServer.class);
    if (proxies.isEmpty()) {
      return;
    }
    if (proxies.size() > 1) {
      log.atWarn()
          .addArgument(this::getServerId)
          .addArgument(proxies::size)
          .log(
              "Canopy '{}' has no explicit tigerProxyUrl and there are {} sibling tigerProxy"
                  + " servers — auto-wire skipped. Set canopy.tigerProxyUrl explicitly.");
      return;
    }
    TigerProxyServer proxy = proxies.get(0);
    var tpc = proxy.getConfiguration().getTigerProxyConfiguration();
    if (tpc == null || tpc.getAdminPort() <= 0) {
      log.atWarn()
          .addArgument(this::getServerId)
          .addArgument(proxy::getServerId)
          .log(
              "Canopy '{}': cannot auto-wire tigerProxyUrl — sibling proxy '{}' has no usable"
                  + " adminPort.");
      return;
    }
    String placeholder = "${" + proxy.getServerId() + ".adminUrl}";
    canopyConfig.setTigerProxyUrl(placeholder);
    getConfiguration().addDependsUpon(proxy.getServerId());
    if (canopyConfig.getControlMode() == ControlMode.NONE) {
      canopyConfig.setControlMode(ControlMode.ROUTE_PER_HOST);
    }
    log.atInfo()
        .addArgument(this::getServerId)
        .addArgument(placeholder)
        .addArgument(proxy::getServerId)
        .addArgument(canopyConfig::getControlMode)
        .addArgument(() -> getConfiguration().getDependsUpon())
        .log(
            "Canopy '{}': auto-wired tigerProxyUrl={} from sibling tigerProxy '{}'"
                + " (controlMode={}, dependsUpon={}).");
  }

  /**
   * Matches {@code ${<serverId>.adminUrl}} — the placeholder shape published by {@link
   * de.gematik.test.tiger.testenvmgr.servers.TigerProxyServer} for sibling-proxy URLs. Used by
   * {@link #autoWireDependsUponFromPlaceholders()} to identify the proxy ids canopy must wait for.
   * Server ids are constrained by Tiger to letters, digits, dash and underscore.
   */
  private static final Pattern ADMIN_URL_PLACEHOLDER =
      Pattern.compile("\\$\\{([A-Za-z0-9_-]+)\\.adminUrl\\}");

  /**
   * Scans the top-level {@code tigerProxyUrl} <em>and</em> every {@code
   * proxiedHosts[*].tigerProxyUrl} override for {@code ${<serverId>.adminUrl}} placeholders and,
   * for every referenced server, appends a {@code dependsUpon} edge so canopy is guaranteed to
   * start <em>after</em> all proxies it routes through. Complements {@link
   * #autoWireTigerProxyUrl()}, which only fires when the top-level URL is blank — this scan also
   * covers the case where the user wrote the placeholder explicitly. Hard-coded URLs (no {@code
   * ${...}}) are tolerated (no edge added — the user is on their own there). Idempotent: {@link
   * CfgServer#addDependsUpon(String)} de-duplicates, and we deliberately don't touch {@code
   * controlMode} or any other field — auto-wiring depth is "boot order only" here.
   */
  private void autoWireDependsUponFromPlaceholders() {
    addDependsUponForPlaceholdersIn(canopyConfig.getTigerProxyUrl(), "top-level tigerProxyUrl");
    if (canopyConfig.getProxiedHosts() != null) {
      for (var ph : canopyConfig.getProxiedHosts()) {
        addDependsUponForPlaceholdersIn(
            ph.getTigerProxyUrl(), "per-host override on '" + ph.getHost() + "'");
      }
    }
  }

  private void addDependsUponForPlaceholdersIn(String url, String contextForLog) {
    if (StringUtils.isBlank(url)) {
      return;
    }
    Matcher m = ADMIN_URL_PLACEHOLDER.matcher(url);
    while (m.find()) {
      String referencedServerId = m.group(1);
      if (referencedServerId.equals(getServerId())) {
        // Self-reference would create a cycle; skip and let the env mgr's cycle check surface
        // anything genuinely cyclic via other edges.
        continue;
      }
      getConfiguration().addDependsUpon(referencedServerId);
      log.atInfo()
          .addArgument(this::getServerId)
          .addArgument(referencedServerId)
          .addArgument(contextForLog)
          .log("Canopy '{}': adding dependsUpon='{}' for {}.");
    }
  }

  @Override
  public void performStartup() {
    // Late-resolve the auto-wired tigerProxyUrl placeholder now that any sibling
    // tigerProxy has booted (dependsUpon was injected in doPrepareDependencies, so the
    // env mgr guarantees that ordering). Explicit user URLs pass through unchanged.
    if (StringUtils.isNotBlank(canopyConfig.getTigerProxyUrl())) {
      canopyConfig.setTigerProxyUrl(
          TigerGlobalConfiguration.resolvePlaceholders(canopyConfig.getTigerProxyUrl()));
    }
    // Same treatment for any per-entry tigerProxyUrl overrides — by now every referenced
    // proxy is RUNNING and its ${<id>.adminUrl} placeholder resolves cleanly.
    if (canopyConfig.getProxiedHosts() != null) {
      for (var ph : canopyConfig.getProxiedHosts()) {
        if (StringUtils.isNotBlank(ph.getTigerProxyUrl())) {
          ph.setTigerProxyUrl(TigerGlobalConfiguration.resolvePlaceholders(ph.getTigerProxyUrl()));
        }
      }
    }

    String image =
        TigerGlobalConfiguration.resolvePlaceholders(
            StringUtils.firstNonBlank(canopyConfig.getImage(), DEFAULT_IMAGE));
    canopyConfig.setImage(image);
    statusMessage("Starting CANOPY container from image " + image);

    container =
        new GenericContainer<>(DockerImageName.parse(image))
            .withExposedPorts(8080, canopyConfig.getDnsPort())
            // Make host.docker.internal resolvable on Linux daemons too (Docker Desktop
            // sets this up automatically; vanilla dockerd does not).
            .withExtraHost("host.docker.internal", "host-gateway");

    canopyConfig.getExtraEnv().forEach(container::withEnv);
    container.withEnv("SPRING_APPLICATION_JSON", buildSpringApplicationJson());

    applyExplicitHostPorts(container);

    container.waitingFor(
        Wait.forHttp("/actuator/health")
            .forPort(8080)
            .forStatusCode(200)
            .withStartupTimeout(Duration.ofSeconds(60)));

    container.start();

    publishPlaceholders();
    adminClient = new CanopyAdminClient(URI.create(getBaseUrl()));

    statusMessage(
        "CANOPY '"
            + getServerId()
            + "' running, admin="
            + getBaseUrl()
            + ", dns="
            + container.getHost()
            + ":"
            + getDnsHostPort());
  }

  /**
   * Builds the {@code SPRING_APPLICATION_JSON} blob that lets the canopy container hydrate its own
   * {@code CanopyConfiguration} from the user's Tiger-side YAML in one shot. Only the
   * runtime-relevant fields are forwarded — Docker knobs ({@code image}, {@code adminPort}, {@code
   * dnsHostPort}, {@code networks}, {@code extraEnv}) stay on the Tiger side and never enter the
   * payload. Done explicitly via a small {@link CanopyRuntimePayload} envelope so a future
   * tightening of canopy's config binding (e.g. {@code
   * spring.jackson.deserialization.FAIL_ON_UNKNOWN_PROPERTIES}) cannot break us.
   */
  String buildSpringApplicationJson() {
    try {
      CanopyRuntimePayload payload = CanopyRuntimePayload.from(canopyConfig);
      // Wrap under "canopy.*" so it binds to the runtime CanopyConfiguration prefix.
      Map<String, Object> envelope = Map.of("canopy", payload);
      return SAJ_MAPPER.writeValueAsString(envelope);
    } catch (Exception e) {
      throw new TigerEnvironmentStartupException(
          "Server '%s': failed to build SPRING_APPLICATION_JSON: %s",
          e, getServerId(), e.getMessage());
    }
  }

  /**
   * Runtime-only mirror of {@code de.gematik.test.tiger.canopy.config.CanopyConfiguration} (the
   * Spring-bound type inside the canopy container). Field names match one-for-one so the canopy
   * side binds without surprises. Docker-only knobs from {@link TigerCanopyConfiguration} are
   * intentionally absent.
   */
  static final class CanopyRuntimePayload {

    @JsonProperty("dnsPort")
    final int dnsPort;

    @JsonProperty("tigerProxyUrl")
    final String tigerProxyUrl;

    @JsonProperty("proxiedHosts")
    final List<Map<String, Object>> proxiedHosts;

    @JsonProperty("controlMode")
    final ControlMode controlMode;

    @JsonProperty("upstreamDnsServers")
    final List<String> upstreamDnsServers;

    @JsonProperty("defaultTtlSeconds")
    final int defaultTtlSeconds;

    @JsonProperty("proxyClientHttpVersion")
    final HttpVersion proxyClientHttpVersion;

    private CanopyRuntimePayload(
        int dnsPort,
        String tigerProxyUrl,
        List<Map<String, Object>> proxiedHosts,
        ControlMode controlMode,
        List<String> upstreamDnsServers,
        int defaultTtlSeconds,
        HttpVersion proxyClientHttpVersion) {
      this.dnsPort = dnsPort;
      this.tigerProxyUrl = tigerProxyUrl;
      this.proxiedHosts = proxiedHosts;
      this.controlMode = controlMode;
      this.upstreamDnsServers = upstreamDnsServers;
      this.defaultTtlSeconds = defaultTtlSeconds;
      this.proxyClientHttpVersion = proxyClientHttpVersion;
    }

    static CanopyRuntimePayload from(TigerCanopyConfiguration src) {
      List<Map<String, Object>> hosts = new ArrayList<>();
      if (src.getProxiedHosts() != null) {
        for (var ph : src.getProxiedHosts()) {
          Map<String, Object> entry = new java.util.LinkedHashMap<>();
          entry.put("host", ph.getHost());
          entry.put("matchType", ph.getMatchType());
          // Per-entry tigerProxyUrl override is omitted when blank so canopy falls back to the
          // top-level default. NON_NULL inclusion on the mapper drops null fields anyway, but
          // we skip explicitly to keep the wire shape predictable for inspection.
          if (StringUtils.isNotBlank(ph.getTigerProxyUrl())) {
            entry.put("tigerProxyUrl", ph.getTigerProxyUrl());
          }
          hosts.add(entry);
        }
      }
      return new CanopyRuntimePayload(
          src.getDnsPort(),
          src.getTigerProxyUrl(),
          hosts,
          src.getControlMode(),
          src.getUpstreamDnsServers(),
          src.getDefaultTtlSeconds(),
          src.getProxyClientHttpVersion());
    }
  }

  private void applyExplicitHostPorts(GenericContainer<?> container) {
    int adminHostPort = canopyConfig.getAdminPort();
    int dnsHostPort = canopyConfig.getDnsHostPort();
    int dnsContainerPort = canopyConfig.getDnsPort();
    if (adminHostPort == 0 && dnsHostPort == 0) {
      return;
    }
    Consumer<CreateContainerCmd> binder =
        cmd -> {
          var existing = cmd.getHostConfig().getPortBindings();
          var current = existing == null ? new Ports() : existing;
          if (adminHostPort > 0) {
            current.bind(ExposedPort.tcp(8080), Ports.Binding.bindPort(adminHostPort));
          }
          if (dnsHostPort > 0) {
            current.bind(ExposedPort.tcp(dnsContainerPort), Ports.Binding.bindPort(dnsHostPort));
            current.bind(ExposedPort.udp(dnsContainerPort), Ports.Binding.bindPort(dnsHostPort));
          }
          cmd.getHostConfig().withPortBindings(current);
        };
    container.withCreateContainerCmdModifier(binder);
  }

  private void publishPlaceholders() {
    String prefix = getServerId() + ".";
    TigerGlobalConfiguration.putValue(prefix + "baseUrl", getBaseUrl());
    String dnsAddress = StringUtils.firstNonBlank(getContainerNetworkIp(), container.getHost());
    TigerGlobalConfiguration.putValue(prefix + "dnsAddress", dnsAddress);
    TigerGlobalConfiguration.putValue(prefix + "dnsPort", Integer.toString(getDnsHostPort()));
    TigerGlobalConfiguration.putValue(prefix + "containerName", container.getContainerName());
  }

  /**
   * Returns the canopy container's primary docker-network IP (first network attached), or {@code
   * null} when the container isn't running or has no networks yet. Visible-for-test and reused by
   * {@link CanopyDnsAutoInjector}.
   */
  String getContainerNetworkIp() {
    if (container == null || !container.isRunning()) {
      return null;
    }
    var info = container.getContainerInfo();
    if (info == null || info.getNetworkSettings() == null) {
      return null;
    }
    var networks = info.getNetworkSettings().getNetworks();
    if (networks == null || networks.isEmpty()) {
      return null;
    }
    return networks.values().stream()
        .map(com.github.dockerjava.api.model.ContainerNetwork::getIpAddress)
        .filter(addr -> addr != null && !addr.isBlank())
        .findFirst()
        .orElse(null);
  }

  private String getBaseUrl() {
    int adminHostPort =
        canopyConfig.getAdminPort() > 0
            ? canopyConfig.getAdminPort()
            : container.getMappedPort(8080);
    return "http://" + container.getHost() + ":" + adminHostPort;
  }

  private int getDnsHostPort() {
    return canopyConfig.getDnsHostPort() > 0
        ? canopyConfig.getDnsHostPort()
        : container.getMappedPort(canopyConfig.getDnsPort());
  }

  /** Returns an admin client pre-pointed at this server's REST API. {@code null} until started. */
  public CanopyAdminClient getAdminClient() {
    return adminClient;
  }

  /** Visible-for-test. */
  TigerCanopyConfiguration getCanopyConfig() {
    return canopyConfig;
  }

  /** Visible-for-test. */
  GenericContainer<?> getContainer() {
    return container;
  }

  @Override
  public void shutdown() {
    if (container != null && container.isRunning()) {
      statusMessage("Stopping CANOPY container '" + getServerId() + "'");
      try {
        container.stop();
      } catch (RuntimeException e) {
        log.atWarn()
            .addArgument(this::getServerId)
            .addArgument(e::getMessage)
            .log("Error while stopping CANOPY '{}': {}");
      }
    }
    setStatus(TigerServerStatus.STOPPED, "CANOPY " + getServerId() + " stopped");
  }
}
