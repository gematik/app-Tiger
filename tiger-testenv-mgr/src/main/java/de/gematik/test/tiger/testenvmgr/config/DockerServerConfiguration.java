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
package de.gematik.test.tiger.testenvmgr.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * YAML-bound configuration for the {@code docker} server type. Read by {@code DockerServer} via
 * {@code AbstractTigerServer#readTypeSpecificConfig("docker", DockerServerConfiguration.class)} —
 * i.e. it goes through the typed-config slot, not through a {@link CfgServer} subclass,
 * deliberately exercising the SPI from an in-tree consumer.
 *
 * <p>User-facing YAML:
 *
 * <pre>{@code
 * servers:
 *   myApp:
 *     type: docker
 *     docker:
 *       image: nginx:alpine
 *       exposedPorts: [80]
 *       env:
 *         LOG_LEVEL: debug
 *       waitStrategy:
 *         kind: portsListening
 * }</pre>
 *
 * <p>The {@code dnsServers} list is what the canopy auto-DNS injector targets when its subscriber
 * receives a {@code BeforeContainerStartEvent}.
 */
@Data
@NoArgsConstructor
public class DockerServerConfiguration {

  /** Container image with tag, e.g. {@code nginx:1.27-alpine}. Required. */
  private String image;

  /** Container-side ports to publish. Testcontainers maps each to a free host port at start. */
  private List<Integer> exposedPorts = new ArrayList<>();

  /**
   * Explicit container-port → host-port mappings. Entries here override Testcontainers' dynamic
   * mapping for the listed ports; ports listed only in {@link #exposedPorts} keep the dynamic
   * mapping.
   */
  private Map<Integer, Integer> portMappings = new LinkedHashMap<>();

  /** Environment variables passed to the container. */
  private Map<String, String> env = new LinkedHashMap<>();

  /**
   * DNS servers used by the container. Empty means: inherit from the Docker daemon (usually the
   * host's). Subscribers to {@code BeforeContainerStartEvent} may mutate the carrier's DNS list
   * pre-start (this is how the canopy auto-DNS injector works).
   */
  private List<String> dnsServers = new ArrayList<>();

  /** Docker networks to attach the container to. */
  private List<String> networks = new ArrayList<>();

  /** Optional command/CMD override. */
  private List<String> command = new ArrayList<>();

  /**
   * If {@code true}, this server explicitly opts <em>out</em> of canopy auto-DNS injection. Default
   * {@code false} → canopy may inject when present. The field lives here (in the core-owned config)
   * intentionally as a generic "skip DNS mutation" switch; the canopy module reads it without core
   * knowing about canopy.
   */
  private boolean injectDns = true;

  /** Per-server wait strategy. Default: wait for exposed ports to be listening. */
  private WaitStrategyConfig waitStrategy = new WaitStrategyConfig();

  /**
   * Wait-strategy union. Discriminated by {@link #kind}. Fields not relevant to the chosen kind are
   * ignored.
   */
  @Data
  @NoArgsConstructor
  public static class WaitStrategyConfig {
    public enum Kind {
      /** Wait until every exposed port responds to a TCP connect. (Testcontainers default.) */
      PORTS_LISTENING,
      /** Wait until {@link #httpPath} on {@link #httpPort} returns {@link #httpStatus}. */
      HTTP,
      /** Wait until {@link #logPattern} matches a line in the container log. */
      LOG,
      /**
       * No explicit Tiger-side wait strategy — Testcontainers falls back to its default log-based
       * "container started" probe. Use for fire-and-forget workloads (sidecars, one-shot scripts)
       * that have no listening port and emit no recognizable log line.
       */
      NONE
    }

    private Kind kind = Kind.NONE;
    private int httpPort;
    private String httpPath = "/";
    private int httpStatus = 200;
    private String logPattern;

    /** Maximum time to wait for the strategy to succeed. */
    private int timeoutSeconds = 60;
  }
}
