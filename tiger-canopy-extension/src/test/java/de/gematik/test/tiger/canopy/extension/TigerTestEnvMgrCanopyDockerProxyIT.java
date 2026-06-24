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

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

/**
 * End-to-end smoke test for the canopy / docker / tigerProxy trio booted via the standard {@link
 * TigerTestEnvMgr#setUpEnvironment()} entry point.
 *
 * <p>Every other IT in this package drives {@code prepareDependencies()} and {@code
 * performStartup()} by hand. This test exists to catch integration regressions that those bypass —
 * graph build, cycle detection, fan-out ordering, lifecycle event publishing and end-to-end
 * placeholder resolution.
 *
 * <p>Auto-skipped when:
 *
 * <ul>
 *   <li>no Docker daemon is reachable, OR
 *   <li>{@link CanopyItImageResolver} cannot resolve a usable canopy image (neither the system
 *       property {@code tiger.canopy.it.image} nor any candidate tag is locally available).
 * </ul>
 */
class TigerTestEnvMgrCanopyDockerProxyIT {

  /**
   * Resolved canopy image is injected as a system property so the {@code @TigerTest} YAML can refer
   * to it via the {@code ${tiger.canopy.it.image}} placeholder (the YAML body is a compile-time
   * constant — there is no other way to thread the resolved tag through).
   */
  @BeforeAll
  static void resolveCanopyImage() {
    assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker daemon not available");
    var image = CanopyItImageResolver.resolve();
    assumeTrue(
        image.isPresent(),
        "No canopy image available — set -D"
            + CanopyItImageResolver.PROPERTY
            + " or pre-load one of: "
            + CanopyItImageResolver.CANDIDATE_TAGS);
    System.setProperty(CanopyItImageResolver.PROPERTY, image.get());
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
          localProxyActive: false
          servers:
            myProxy:
              type: tigerProxy
              tigerProxyConfiguration:
                adminPort: ${free.port.10}
                proxyPort: ${free.port.11}
            myCanopy:
              type: canopy
              canopy:
                image: ${tiger.canopy.it.image}
            app:
              type: docker
              docker:
                image: alpine:3.20
                exposedPorts: []
                command: ["sh", "-c", "while true; do sleep 1; done"]
                waitStrategy: { kind: NONE, timeoutSeconds: 30 }
          """)
  void allThreeServers_reachRunningAndPlaceholdersResolve(TigerTestEnvMgr envMgr) {
    // setUpEnvironment already ran via TigerExtension. Verify the end state.
    assertThat(envMgr.getServers().keySet())
        .as("all configured servers are registered")
        .containsExactlyInAnyOrder("myProxy", "myCanopy", "app");

    envMgr
        .getServers()
        .values()
        .forEach(
            s ->
                assertThat(s.getStatus())
                    .as("server '%s' should be RUNNING", s.getServerId())
                    .isEqualTo(TigerServerStatus.RUNNING));

    // Implicit dependsUpon edges injected by the two prepareDependencies() hooks.
    assertThat(envMgr.getServers().get("myCanopy").getConfiguration().getDependsUpon())
        .as("canopy auto-wires dependsUpon onto its sibling proxy")
        .contains("myProxy");
    assertThat(envMgr.getServers().get("app").getConfiguration().getDependsUpon())
        .as("docker target auto-wires dependsUpon onto the canopy")
        .contains("myCanopy");

    // TigerProxyServer's published placeholders must be resolvable.
    assertThat(TigerGlobalConfiguration.readString("myProxy.adminUrl", null))
        .as("myProxy.adminUrl placeholder is published")
        .isNotBlank()
        .startsWith("http://");
    assertThat(TigerGlobalConfiguration.readString("myProxy.proxyUrl", null))
        .as("myProxy.proxyUrl placeholder is published")
        .isNotBlank();

    // CanopyServer's published placeholders must be resolvable.
    assertThat(TigerGlobalConfiguration.readString("myCanopy.baseUrl", null))
        .as("myCanopy.baseUrl placeholder is published")
        .isNotBlank()
        .startsWith("http://");
    assertThat(TigerGlobalConfiguration.readString("myCanopy.dnsPort", null))
        .as("myCanopy.dnsPort placeholder is published")
        .isNotBlank();

    // CanopyServer auto-wires its tigerProxyUrl to the resolved adminUrl placeholder.
    var canopy = (CanopyServer) envMgr.getServers().get("myCanopy");
    assertThat(canopy.getCanopyConfig().getTigerProxyUrl())
        .as("canopy.tigerProxyUrl was auto-wired AND lazily resolved at performStartup")
        .isNotBlank()
        .doesNotContain("${") // no unresolved placeholder
        .startsWith("http://");
  }
}
