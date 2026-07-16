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
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * End-to-end integration test for {@link CanopyServer}. Boots a real canopy container from a
 * locally available image and verifies the canopy lifecycle:
 *
 * <ul>
 *   <li>image is pulled / instantiated;
 *   <li>the {@code SPRING_APPLICATION_JSON} envelope is accepted and the canopy reaches healthy
 *       state on {@code /actuator/health};
 *   <li>placeholders {@code <id>.baseUrl}, {@code <id>.dnsAddress}, {@code <id>.dnsPort} are
 *       published into {@link TigerGlobalConfiguration};
 *   <li>{@link CanopyServer#getAdminClient()} returns a non-{@code null} client after start.
 * </ul>
 *
 * <p>Image discovery is delegated to {@link CanopyItImageResolver}: the system property {@code
 * tiger.canopy.it.image} wins, otherwise the local Docker daemon is probed for a known canopy tag.
 * Auto-skipped when:
 *
 * <ul>
 *   <li>no Docker daemon is reachable, OR
 *   <li>neither the system property nor any candidate tag resolves to a usable image.
 * </ul>
 */
class CanopyServerStartupIT {

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void startsRealCanopyContainerAndPublishesPlaceholders(TigerTestEnvMgr mgr) {
    assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker daemon not available");
    Optional<String> resolved = CanopyItImageResolver.resolve();
    assumeTrue(
        resolved.isPresent(),
        "No canopy image available — set -D"
            + CanopyItImageResolver.PROPERTY
            + " or pre-load one of: "
            + CanopyItImageResolver.CANDIDATE_TAGS);
    String image = resolved.get();

    String yaml =
        """
        image: %s
        controlMode: NONE
        dnsPort: 53
        """
            .formatted(image);
    JsonNode canopyBlock = new YAMLMapper().readTree(yaml);

    CfgServer cfg = new CfgServer().setType("canopy").setHostname("canopyit");
    cfg.setTypeSpecificConfigEntry("canopy", canopyBlock);

    CanopyServer s = new CanopyServer("canopyit", cfg, mgr);
    mgr.getServers().put("canopyit", s);
    try {
      s.prepareDependencies();
      s.assertThatConfigurationIsCorrect();
      s.performStartup();

      assertThat(TigerGlobalConfiguration.readString("canopyit.baseUrl", null))
          .as("canopyit.baseUrl placeholder should be published after start")
          .isNotBlank()
          .startsWith("http://");
      assertThat(TigerGlobalConfiguration.readString("canopyit.dnsAddress", null))
          .as(
              "canopyit.dnsAddress placeholder must be an IPv4 dotted-quad — "
                  + "Docker rejects hostnames (e.g. 'localhost') in --dns with ParseAddr")
          .isNotBlank()
          .matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
      assertThat(TigerGlobalConfiguration.readString("canopyit.dnsPort", null))
          .as("canopyit.dnsPort placeholder should be published after start")
          .isNotBlank();
      assertThat(s.getAdminClient())
          .as("adminClient should be wired once the canopy is running")
          .isNotNull();
    } finally {
      s.shutdown();
      mgr.getServers().remove("canopyit");
    }
  }
}
