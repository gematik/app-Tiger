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
package de.gematik.test.tiger.canopy.config;

import de.gematik.test.tiger.canopy.client.config.ControlMode;
import de.gematik.test.tiger.canopy.client.config.HttpVersion;
import de.gematik.test.tiger.canopy.client.config.MatchType;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the CANOPY DNS server. Bound to the {@code canopy.*} properties (see {@code
 * application.yaml}). Common environment-variable bindings:
 *
 * <ul>
 *   <li>{@code TIGER_PROXY_URL} → {@link #tigerProxyUrl}
 *   <li>{@code CANOPY_PROXIEDHOSTS_<n>_HOST} / {@code CANOPY_PROXIEDHOSTS_<n>_MATCHTYPE} → indexed
 *       entries of {@link #proxiedHosts}
 *   <li>{@code CANOPY_DNS_PORT} → {@link #dnsPort}
 * </ul>
 *
 * <p>The {@link MatchType}, {@link ControlMode} and {@link HttpVersion} enums live in {@code
 * tiger-canopy-client} so the REST client can reuse them without a Spring dependency.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "canopy")
public class CanopyConfiguration {

  /** UDP/TCP port on which the DNS server listens. Default 53 (requires CAP_NET_BIND_SERVICE). */
  private int dnsPort = 53;

  /** URL of the Tiger proxy that hostnames in {@link #proxiedHosts} should be redirected to. */
  private String tigerProxyUrl;

  /** Initial list of hostnames to be intercepted on startup. */
  private List<ProxiedHost> proxiedHosts = new ArrayList<>();

  /** How CANOPY should configure the Tiger proxy when entries change. */
  private ControlMode controlMode = ControlMode.NONE;

  /**
   * Optional override of upstream DNS servers used for non-proxied resolution. If empty, {@code
   * /etc/resolv.conf} is consulted.
   */
  private List<String> upstreamDnsServers = new ArrayList<>();

  /** TTL (seconds) returned for synthesized answers pointing to the Tiger proxy. */
  private int defaultTtlSeconds = 30;

  /** HTTP protocol version used by the stage-2 admin client when talking to the Tiger proxy. */
  private HttpVersion proxyClientHttpVersion = HttpVersion.AUTO;

  /** Configuration entry for a proxied hostname. */
  @Getter
  @Setter
  public static class ProxiedHost {
    private String host;
    private MatchType matchType = MatchType.EXACT;

    /**
     * Optional per-entry override of {@link CanopyConfiguration#tigerProxyUrl}. When non-null, DNS
     * answers for this host (and, in {@link ControlMode#ROUTE_PER_HOST} mode, the route registered
     * on the Tiger proxy) target the override URL instead of the global default.
     */
    private String tigerProxyUrl;
  }
}
