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
package de.gematik.test.tiger.testenvmgr.events;

import de.gematik.test.tiger.testenvmgr.servers.AbstractTigerServer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Getter;

/**
 * Fired by container-spawning server types (e.g. the {@code DockerServer}) immediately before
 * {@code container.start()}. Carries a <em>mutable</em> view of the to-be-started container's DNS
 * list, network list and environment so that subscribers (e.g. the canopy auto-DNS injector) can
 * mutate it pre-start.
 *
 * <p>Contract:
 *
 * <ul>
 *   <li>The producer must apply the carrier's state to the actual container builder <em>after</em>
 *       publishing the event.
 *   <li>Subscribers must not block — listener invocation is synchronous on the startup thread.
 *   <li>The carrier's fields are intentionally minimal (DNS, networks, env). Future extensions may
 *       add fields; subscribers reading only what they know about stay forwards-compatible.
 * </ul>
 *
 * <p>See {@code doc/adr/canopy-extension-repo-extraction.md} for the SPI rationale.
 */
@Getter
public final class BeforeContainerStartEvent implements TigerLifecycleEvent {

  private final AbstractTigerServer server;
  private final List<String> dnsServers;
  private final List<String> networks;
  private final Map<String, String> extraEnv;

  public BeforeContainerStartEvent(
      AbstractTigerServer server,
      List<String> initialDnsServers,
      List<String> initialNetworks,
      Map<String, String> initialExtraEnv) {
    this.server = Objects.requireNonNull(server, "server");
    // Defensive copies into mutable backing collections so subscribers can edit freely.
    this.dnsServers =
        new CopyOnWriteArrayList<>(initialDnsServers == null ? List.of() : initialDnsServers);
    this.networks =
        new CopyOnWriteArrayList<>(initialNetworks == null ? List.of() : initialNetworks);
    this.extraEnv = new LinkedHashMap<>(initialExtraEnv == null ? Map.of() : initialExtraEnv);
  }

  /** Prepend a DNS server. No-op if {@code dnsServer} is already first in the list. */
  public void prependDnsServer(String dnsServer) {
    Objects.requireNonNull(dnsServer, "dnsServer");
    if (dnsServers.isEmpty() || !dnsServers.get(0).equals(dnsServer)) {
      dnsServers.remove(dnsServer);
      dnsServers.add(0, dnsServer);
    }
  }

  /** Append a network. No-op if {@code network} is already present. */
  public void addNetwork(String network) {
    Objects.requireNonNull(network, "network");
    if (!networks.contains(network)) {
      networks.add(network);
    }
  }

  /** Set/overwrite an environment variable. */
  public void addEnvVar(String key, String value) {
    Objects.requireNonNull(key, "key");
    extraEnv.put(key, value);
  }
}
