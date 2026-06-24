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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import org.junit.jupiter.api.Test;

/**
 * Pre-startup validation tests for {@link DockerServer} that do <em>not</em> spawn a container. The
 * end-to-end "actually start an image and assert placeholders" lives in {@code
 * DockerServerStartupIT}.
 */
class DockerServerValidationTest {

  private static CfgServer cfgWithDockerBlock(String json) throws Exception {
    CfgServer cfg = new CfgServer().setType("docker").setHostname("dockerunit");
    if (json != null) {
      JsonNode tree = new ObjectMapper().readTree(json);
      cfg.setTypeSpecificConfigEntry("docker", tree);
    }
    return cfg;
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void assertConfig_throws_whenDockerBlockMissing(TigerTestEnvMgr mgr) throws Exception {
    DockerServer s = new DockerServer("d1", cfgWithDockerBlock(null), mgr);

    assertThatThrownBy(s::assertThatConfigurationIsCorrect)
        .isInstanceOf(TigerEnvironmentStartupException.class)
        .hasMessageContaining("d1")
        .hasMessageContaining("docker:");
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void assertConfig_throws_whenImageMissing(TigerTestEnvMgr mgr) throws Exception {
    DockerServer s = new DockerServer("d1", cfgWithDockerBlock("{\"exposedPorts\":[80]}"), mgr);

    assertThatThrownBy(s::assertThatConfigurationIsCorrect)
        .isInstanceOf(TigerEnvironmentStartupException.class)
        .hasMessageContaining("docker.image");
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void assertConfig_acceptsMinimalImageOnly(TigerTestEnvMgr mgr) throws Exception {
    DockerServer s =
        new DockerServer("d1", cfgWithDockerBlock("{\"image\":\"alpine:latest\"}"), mgr);

    s.assertThatConfigurationIsCorrect();

    assertThat(s.getDockerConfig()).isNotNull();
    assertThat(s.getDockerConfig().getImage()).isEqualTo("alpine:latest");
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void assertConfig_throws_whenMultipleNetworksRequested(TigerTestEnvMgr mgr) throws Exception {
    // B2 regression: multi-network attach is not supported in v1; must fail loudly, not
    // silently drop the second/third entries.
    DockerServer s =
        new DockerServer(
            "d1", cfgWithDockerBlock("{\"image\":\"alpine\",\"networks\":[\"a\",\"b\"]}"), mgr);

    assertThatThrownBy(s::assertThatConfigurationIsCorrect)
        .isInstanceOf(TigerEnvironmentStartupException.class)
        .hasMessageContaining("multi-network")
        .hasMessageContaining("[a, b]");
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void assertConfig_throws_whenPortsListeningWithoutExposedPorts(TigerTestEnvMgr mgr)
      throws Exception {
    // B3 regression: PORTS_LISTENING with empty exposedPorts must fail loudly instead of
    // silently degrading to Testcontainers' default log probe.
    DockerServer s =
        new DockerServer(
            "d1",
            cfgWithDockerBlock(
                "{\"image\":\"alpine\",\"waitStrategy\":{\"kind\":\"PORTS_LISTENING\"}}"),
            mgr);

    assertThatThrownBy(s::assertThatConfigurationIsCorrect)
        .isInstanceOf(TigerEnvironmentStartupException.class)
        .hasMessageContaining("PORTS_LISTENING")
        .hasMessageContaining("docker.exposedPorts")
        .hasMessageContaining("kind=NONE");
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void assertConfig_throws_whenLogStrategyWithoutPattern(TigerTestEnvMgr mgr) throws Exception {
    DockerServer s =
        new DockerServer(
            "d1",
            cfgWithDockerBlock("{\"image\":\"alpine\",\"waitStrategy\":{\"kind\":\"LOG\"}}"),
            mgr);

    assertThatThrownBy(s::assertThatConfigurationIsCorrect)
        .isInstanceOf(TigerEnvironmentStartupException.class)
        .hasMessageContaining("LOG")
        .hasMessageContaining("logPattern");
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void assertConfig_acceptsNoneStrategyWithNoPorts(TigerTestEnvMgr mgr) throws Exception {
    DockerServer s =
        new DockerServer(
            "d1",
            cfgWithDockerBlock("{\"image\":\"alpine\",\"waitStrategy\":{\"kind\":\"NONE\"}}"),
            mgr);

    s.assertThatConfigurationIsCorrect();
    assertThat(s.getDockerConfig()).isNotNull();
  }
}
