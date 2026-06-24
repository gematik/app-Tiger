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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import de.gematik.test.tiger.testenvmgr.config.DockerServerConfiguration.WaitStrategyConfig.Kind;
import org.junit.jupiter.api.Test;

/**
 * YAML round-trip + defaults coverage for the {@link DockerServerConfiguration} POJO. These tests
 * do not require a Docker daemon.
 */
class DockerServerConfigurationTest {

  private final YAMLMapper yaml = new YAMLMapper();

  @Test
  void defaults_areSensible() {
    DockerServerConfiguration cfg = new DockerServerConfiguration();
    assertThat(cfg.getImage()).isNull();
    assertThat(cfg.getExposedPorts()).isEmpty();
    assertThat(cfg.getPortMappings()).isEmpty();
    assertThat(cfg.getEnv()).isEmpty();
    assertThat(cfg.getDnsServers()).isEmpty();
    assertThat(cfg.getNetworks()).isEmpty();
    assertThat(cfg.getCommand()).isEmpty();
    assertThat(cfg.isInjectDns()).isTrue();
    assertThat(cfg.getWaitStrategy().getKind()).isEqualTo(Kind.NONE);
    assertThat(cfg.getWaitStrategy().getTimeoutSeconds()).isEqualTo(60);
    assertThat(cfg.getWaitStrategy().getHttpPath()).isEqualTo("/");
    assertThat(cfg.getWaitStrategy().getHttpStatus()).isEqualTo(200);
  }

  @Test
  void minimalYaml_deserializes() throws Exception {
    String src =
        """
        image: nginx:alpine
        exposedPorts: [80]
        """;
    DockerServerConfiguration cfg = yaml.readValue(src, DockerServerConfiguration.class);
    assertThat(cfg.getImage()).isEqualTo("nginx:alpine");
    assertThat(cfg.getExposedPorts()).containsExactly(80);
  }

  @Test
  void fullyPopulatedYaml_roundTrips() throws Exception {
    String src =
        """
        image: my.registry/nginx:1.27-alpine
        exposedPorts: [80, 443]
        portMappings:
          80: 8080
        env:
          LOG_LEVEL: debug
          FEATURE_X: "on"
        dnsServers: [9.9.9.9, 1.1.1.1]
        networks: [tiger-net]
        command: [nginx, -g, "daemon off;"]
        injectDns: false
        waitStrategy:
          kind: HTTP
          httpPort: 80
          httpPath: /health
          httpStatus: 204
          timeoutSeconds: 30
        """;
    DockerServerConfiguration cfg = yaml.readValue(src, DockerServerConfiguration.class);

    assertThat(cfg.getImage()).isEqualTo("my.registry/nginx:1.27-alpine");
    assertThat(cfg.getExposedPorts()).containsExactly(80, 443);
    assertThat(cfg.getPortMappings()).containsEntry(80, 8080);
    assertThat(cfg.getEnv()).containsEntry("LOG_LEVEL", "debug").containsEntry("FEATURE_X", "on");
    assertThat(cfg.getDnsServers()).containsExactly("9.9.9.9", "1.1.1.1");
    assertThat(cfg.getNetworks()).containsExactly("tiger-net");
    assertThat(cfg.getCommand()).containsExactly("nginx", "-g", "daemon off;");
    assertThat(cfg.isInjectDns()).isFalse();
    assertThat(cfg.getWaitStrategy().getKind()).isEqualTo(Kind.HTTP);
    assertThat(cfg.getWaitStrategy().getHttpPort()).isEqualTo(80);
    assertThat(cfg.getWaitStrategy().getHttpPath()).isEqualTo("/health");
    assertThat(cfg.getWaitStrategy().getHttpStatus()).isEqualTo(204);
    assertThat(cfg.getWaitStrategy().getTimeoutSeconds()).isEqualTo(30);

    // round-trip through serialization
    String again = yaml.writeValueAsString(cfg);
    DockerServerConfiguration restored = yaml.readValue(again, DockerServerConfiguration.class);
    assertThat(restored).isEqualTo(cfg);
  }

  @Test
  void unknownFields_toleratedForForwardCompatibility() throws Exception {
    String src =
        """
        image: alpine:latest
        futureFieldFromTomorrow: true
        waitStrategy:
          kind: LOG
          logPattern: ready
          futureKnob: 42
        """;
    var tolerant =
        new YAMLMapper()
            .configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);
    DockerServerConfiguration cfg = tolerant.readValue(src, DockerServerConfiguration.class);
    assertThat(cfg.getImage()).isEqualTo("alpine:latest");
    assertThat(cfg.getWaitStrategy().getKind()).isEqualTo(Kind.LOG);
    assertThat(cfg.getWaitStrategy().getLogPattern()).isEqualTo("ready");
  }
}
