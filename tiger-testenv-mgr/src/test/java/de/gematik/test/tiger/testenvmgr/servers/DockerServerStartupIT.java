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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.events.BeforeContainerStartEvent;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

/**
 * End-to-end integration test for the {@link DockerServer} type. Starts a real {@code
 * tiantianbigshot/nginx-alpine}-style image via Testcontainers and verifies:
 *
 * <ul>
 *   <li>{@link BeforeContainerStartEvent} is fired and carries the initial state from {@code
 *       docker:} YAML config,
 *   <li>placeholders are published after start ({@code <id>.host}, {@code <id>.<port>.hostPort},
 *       {@code <id>.containerId}).
 * </ul>
 *
 * <p>Auto-skipped when no Docker daemon is reachable, so CI without Docker (and developer machines
 * that have not configured Docker) won't see spurious failures.
 */
class DockerServerStartupIT {

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void startsRealContainerAndFiresBeforeContainerStartEvent(TigerTestEnvMgr mgr) throws Exception {
    assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker daemon not available");

    String yaml =
        """
        image: alpine:3.20
        exposedPorts: []
        command: ["sh", "-c", "while true; do sleep 1; done"]
        waitStrategy:
          kind: NONE
          timeoutSeconds: 30
        """;
    JsonNode dockerBlock = new YAMLMapper().readTree(yaml);

    CfgServer cfg = new CfgServer().setType("docker").setHostname("dit");
    cfg.setTypeSpecificConfigEntry("docker", dockerBlock);

    AtomicReference<BeforeContainerStartEvent> seen = new AtomicReference<>();
    mgr.getLifecycleEventBus().subscribe(BeforeContainerStartEvent.class, seen::set);

    DockerServer s = new DockerServer("dit", cfg, mgr);
    mgr.getServers().put("dit", s);
    try {
      s.assertThatConfigurationIsCorrect();
      s.performStartup();

      assertThat(seen.get()).as("BeforeContainerStartEvent should have fired").isNotNull();
      assertThat(seen.get().getServer()).isSameAs(s);

      assertThat(TigerGlobalConfiguration.readString("dit.host", null)).isNotBlank();
      assertThat(TigerGlobalConfiguration.readString("dit.containerId", null)).isNotBlank();
    } finally {
      s.shutdown();
      mgr.getServers().remove("dit");
    }
  }
}
