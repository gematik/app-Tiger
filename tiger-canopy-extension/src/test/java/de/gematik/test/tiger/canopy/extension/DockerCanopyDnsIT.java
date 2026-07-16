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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.events.BeforeContainerStartEvent;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.DockerServer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * End-to-end integration test for the canopy auto-DNS handoff between {@link CanopyServer} and
 * {@link DockerServer}. Verifies that when both servers are in the env:
 *
 * <ul>
 *   <li>{@link CanopyServer#doPrepareDependencies()} registers the auto-DNS subscriber early
 *       (before any container starts);
 *   <li>{@code DockerServer.doPrepareDependencies()} injects an implicit {@code dependsUpon
 *       &lt;canopyId&gt;} edge so the canopy is RUNNING by the time the docker target fires its
 *       {@link BeforeContainerStartEvent};
 *   <li>the {@link de.gematik.test.tiger.canopy.extension.CanopyDnsAutoInjector} prepends the
 *       canopy's dnsAddress to the event's DNS list;
 *   <li>{@link DockerServer} forwards the mutated DNS list to the container builder.
 * </ul>
 *
 * <p>Auto-skipped when:
 *
 * <ul>
 *   <li>no Docker daemon is reachable, OR
 *   <li>{@link CanopyItImageResolver} cannot resolve a usable canopy image (neither the system
 *       property {@code tiger.canopy.it.image} nor any candidate tag is locally available).
 * </ul>
 */
class DockerCanopyDnsIT {

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void canopyAndDockerSibling_autoInjectsDnsAndOrderedStart(TigerTestEnvMgr mgr) {
    assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker daemon not available");
    Optional<String> resolved = CanopyItImageResolver.resolve();
    assumeTrue(
        resolved.isPresent(),
        "No canopy image available — set -D"
            + CanopyItImageResolver.PROPERTY
            + " or pre-load one of: "
            + CanopyItImageResolver.CANDIDATE_TAGS);
    String canopyImage = resolved.get();

    String canopyYaml =
        """
        image: %s
        controlMode: NONE
        """
            .formatted(canopyImage);
    JsonNode canopyBlock = new YAMLMapper().readTree(canopyYaml);
    CfgServer canopyCfg = new CfgServer().setType("canopy").setHostname("canopydnsit");
    canopyCfg.setTypeSpecificConfigEntry("canopy", canopyBlock);

    String dockerYaml =
        """
        image: alpine:3.20
        exposedPorts: []
        command: ["sh", "-c", "while true; do sleep 1; done"]
        waitStrategy:
          kind: NONE
          timeoutSeconds: 30
        """;
    JsonNode dockerBlock = new YAMLMapper().readTree(dockerYaml);
    CfgServer dockerCfg = new CfgServer().setType("docker").setHostname("dnstarget");
    dockerCfg.setTypeSpecificConfigEntry("docker", dockerBlock);

    CanopyServer canopy = new CanopyServer("canopydnsit", canopyCfg, mgr);
    DockerServer target = new DockerServer("dnstarget", dockerCfg, mgr);
    mgr.getServers().put("canopydnsit", canopy);
    mgr.getServers().put("dnstarget", target);

    AtomicReference<BeforeContainerStartEvent> dockerTargetEvent = new AtomicReference<>();
    mgr.getLifecycleEventBus()
        .subscribe(
            BeforeContainerStartEvent.class,
            evt -> {
              if (evt.getServer() == target) {
                dockerTargetEvent.set(evt);
              }
            });

    try {
      canopy.prepareDependencies();
      target.prepareDependencies();

      // Step 3b contract: docker target depends on the canopy implicitly.
      assertThat(target.getConfiguration().getDependsUpon())
          .as("docker target should have implicit dependsUpon on canopy")
          .isEqualTo("canopydnsit");

      canopy.assertThatConfigurationIsCorrect();
      canopy.performStartup();
      target.assertThatConfigurationIsCorrect();
      target.performStartup();

      assertThat(dockerTargetEvent.get())
          .as("docker target should have fired BeforeContainerStartEvent")
          .isNotNull();
      assertThat(dockerTargetEvent.get().getDnsServers())
          .as("canopy auto-DNS injector should have prepended canopy's network IP")
          .isNotEmpty()
          .first()
          .satisfies(
              addr -> {
                // Must be an IPv4 dotted-quad — Docker rejects non-IP DNS entries.
                assertThat(addr).matches("\\d+\\.\\d+\\.\\d+\\.\\d+");
              });
    } finally {
      try {
        target.shutdown();
      } finally {
        canopy.shutdown();
        mgr.getServers().remove("dnstarget");
        mgr.getServers().remove("canopydnsit");
      }
    }
  }
}
