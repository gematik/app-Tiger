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

import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.DockerServerConfiguration;
import de.gematik.test.tiger.testenvmgr.events.BeforeContainerStartEvent;
import de.gematik.test.tiger.testenvmgr.servers.DockerServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto-DNS injector for sibling containers. Subscribes to {@link BeforeContainerStartEvent} on the
 * lifecycle bus; when a container is about to start and a {@link CanopyServer} is present in the
 * env, prepends the canopy's DNS address to the container's {@code dnsServers} list so the
 * container resolves hostnames through canopy by default.
 *
 * <p>Behaviour rules (v1):
 *
 * <ul>
 *   <li>No-op when no canopy is registered.
 *   <li>No-op when more than one canopy is in the env (multi-canopy opt-in is a follow-up).
 *   <li>No-op when the canopy is not yet {@code RUNNING}. {@link CanopyServer} registers this
 *       injector during {@code prepareDependencies()} (before any server starts), so this branch
 *       only triggers if the target docker server is booting <em>before</em> the canopy — which
 *       {@link de.gematik.test.tiger.testenvmgr.servers.DockerServer} prevents by injecting an
 *       implicit {@code dependsUpon &lt;canopyId&gt;} during its own {@code prepareDependencies()}
 *       hook. The branch remains as a safety net for the edge case where a user explicitly clears
 *       that edge.
 *   <li>No-op when the target server is itself a canopy (canopy resolving via canopy is a loop).
 *   <li>No-op when the carrier already has DNS entries — explicit user config wins.
 *   <li>For {@link DockerServer} targets: respects {@code docker.injectDns: false} opt-out.
 *   <li>For non-Docker container producers (future server types): default-on, no opt-out yet — to
 *       be revisited when a second producer appears.
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class CanopyDnsAutoInjector {

  private final TigerTestEnvMgr mgr;
  private final CanopyServer ownCanopy;

  /** Wires this injector into the lifecycle bus. Idempotent across multiple calls (last wins). */
  public void register() {
    mgr.getLifecycleEventBus().subscribe(BeforeContainerStartEvent.class, this::onContainerStart);
  }

  void onContainerStart(BeforeContainerStartEvent event) {
    if (event.getServer() instanceof CanopyServer) {
      return; // canopy → canopy DNS loop
    }
    if (mgr.getServersOfType(CanopyServer.class).size() > 1) {
      log.debug(
          "Auto-DNS injection skipped: more than one CanopyServer in env. "
              + "Multi-canopy targeting will land in a follow-up.");
      return;
    }
    if (!event.getDnsServers().isEmpty()) {
      log.atDebug()
          .addArgument(() -> event.getServer().getServerId())
          .addArgument(event::getDnsServers)
          .log("Auto-DNS injection skipped for '{}': carrier already has explicit DNS entries {}.");
      return;
    }
    if (event.getServer() instanceof DockerServer ds) {
      DockerServerConfiguration cfg = ds.getDockerConfig();
      if (cfg != null && !cfg.isInjectDns()) {
        log.atDebug()
            .addArgument(ds::getServerId)
            .log("Auto-DNS injection skipped for '{}': docker.injectDns=false.");
        return;
      }
    }
    String dns = ownCanopy.getContainerNetworkIp();
    if (dns == null) {
      log.atWarn()
          .addArgument(ownCanopy::getServerId)
          .addArgument(() -> event.getServer().getServerId())
          .log(
              "Auto-DNS injection: canopy '{}' has no resolvable dnsAddress yet. "
                  + "Skipping injection for '{}'. Ensure the target server dependsUpon the canopy.");
      return;
    }
    event.prependDnsServer(dns);
    log.atInfo()
        .addArgument(ownCanopy::getServerId)
        .addArgument(dns)
        .addArgument(() -> event.getServer().getServerId())
        .log(
            "Auto-DNS: prepended canopy '{}' DNS address {} to container DNS list for server '{}'");
  }
}
