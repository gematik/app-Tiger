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

import de.gematik.test.tiger.canopy.client.config.ControlMode;
import de.gematik.test.tiger.canopy.client.config.HttpVersion;
import de.gematik.test.tiger.canopy.client.config.MatchType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * YAML-bound configuration for a CANOPY DNS server orchestrated by the Tiger TestEnv-Mgr (server
 * type {@code canopy}). Sister to {@code TigerProxyConfiguration} on {@link
 * de.gematik.test.tiger.testenvmgr.config.CfgServer}.
 *
 * <p>Runtime fields ({@link #dnsPort}, {@link #tigerProxyUrl}, {@link #proxiedHosts}, {@link
 * #controlMode}, {@link #upstreamDnsServers}, {@link #defaultTtlSeconds}, {@link
 * #proxyClientHttpVersion}) mirror the Spring-bound {@code CanopyConfiguration} of the {@code
 * tiger-canopy} server one-for-one and are forwarded into the container as {@code CANOPY_*} env
 * vars at startup.
 *
 * <p>Docker knobs ({@link #image}, {@link #adminPort}, {@link #dnsHostPort}, {@link #networks},
 * {@link #extraEnv}) only affect how the {@code CanopyServer} launches the Testcontainer.
 *
 * <p>The wire-format enums ({@link MatchType}, {@link ControlMode}, {@link HttpVersion}) are
 * imported from {@code tiger-canopy-client} so this POJO and the runtime share exactly one source
 * of truth.
 */
@Data
@NoArgsConstructor
public class TigerCanopyConfiguration {

  // ----- runtime configuration (forwarded into the container) ------------------------------

  /**
   * UDP/TCP port the DNS server listens on <em>inside</em> the container. Default 53. The host-side
   * port is {@link #dnsHostPort}.
   */
  private int dnsPort = 53;

  /**
   * URL of the sibling Tiger proxy that proxied hosts should be redirected to. Optional; if unset,
   * the {@code CanopyServer} will (in a follow-up) auto-wire it to the sole sibling {@code
   * tigerProxy} server (or the local Tiger proxy as a last resort).
   */
  private String tigerProxyUrl;

  /** Initial registry contents written to the CANOPY at startup. */
  private List<ProxiedHost> proxiedHosts = new ArrayList<>();

  /**
   * How CANOPY interacts with the Tiger proxy as registry mutations occur. Defaults to {@link
   * ControlMode#NONE}; auto-wire bumps this to {@link ControlMode#ROUTE_PER_HOST} when it
   * activates.
   */
  private ControlMode controlMode = ControlMode.NONE;

  /**
   * Upstream DNS servers used for non-proxied resolution. Empty means: let the container pick
   * defaults from {@code /etc/resolv.conf}.
   */
  private List<String> upstreamDnsServers = new ArrayList<>();

  /** TTL (seconds) returned for synthesized answers pointing at the Tiger proxy. */
  private int defaultTtlSeconds = 30;

  /** HTTP protocol version used by the stage-2 admin client when talking to the Tiger proxy. */
  private HttpVersion proxyClientHttpVersion = HttpVersion.AUTO;

  // ----- Docker knobs (consumed by CanopyServer) -------------------------------------------

  /**
   * Container image. Defaults to {@code null}, meaning the {@code CanopyServer} resolves it from
   * the {@code tiger-canopy} jar's {@code pom.properties} (follow-up) and falls back to {@code
   * de.gematik.test/tiger-canopy:latest} for dev builds.
   */
  private String image;

  /**
   * Host port mapped to the CANOPY admin/REST port (8080 in the container). {@code 0} (default)
   * means: pick a free ephemeral port at startup.
   */
  private int adminPort = 0;

  /**
   * Host port mapped to {@link #dnsPort}. {@code 0} (default) means: pick a free ephemeral port at
   * startup. Note that 53 on the host is typically taken on a developer workstation, hence the
   * default of "let Testcontainers choose".
   */
  private int dnsHostPort = 0;

  /**
   * Additional Docker networks to attach the CANOPY container to. The {@code CanopyServer} also
   * creates / joins a per-env shared network (follow-up) so sibling Docker servers can reach the
   * CANOPY by IP; entries here are layered on top.
   */
  private List<String> networks = new ArrayList<>();

  /**
   * Extra environment variables passed verbatim to the container. Keys/values mirror the {@code
   * environment:} section of a docker-compose service. Insertion order is preserved.
   */
  private Map<String, String> extraEnv = new LinkedHashMap<>();

  /** Configuration entry for a proxied hostname. Mirrors the runtime nested type. */
  @Data
  @NoArgsConstructor
  public static class ProxiedHost {
    private String host;
    private MatchType matchType = MatchType.EXACT;

    /**
     * Optional per-entry override for the Tiger proxy this host should be redirected to. When
     * blank, the canopy uses the top-level {@link TigerCanopyConfiguration#tigerProxyUrl} as the
     * default. Lets a single canopy fan out to several reverse proxies — typically one per protocol
     * (HTTP / POP3 / SMTP / …) — without spawning a canopy per protocol.
     *
     * <p>Tiger placeholders ({@code ${<serverId>.adminUrl}}) are honored the same way as on the
     * top-level field: the placeholder is preserved through dependency-graph build and resolved
     * just before the canopy container starts, so the referenced proxy is guaranteed RUNNING by
     * then (a {@code dependsUpon} edge is auto-added).
     */
    private String tigerProxyUrl;
  }
}
