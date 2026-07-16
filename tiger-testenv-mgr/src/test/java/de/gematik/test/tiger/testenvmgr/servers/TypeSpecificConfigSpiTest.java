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

import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import java.util.List;
import lombok.Data;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Tests for the typed-config slot SPI: {@link CfgServer#getTypeSpecificConfig()} and {@link
 * AbstractTigerServer#readTypeSpecificConfig(String, Class)}.
 *
 * <p>The slot is exposed in YAML as <em>bare top-level keys</em> on the server entry (matching the
 * {@code tigerProxyConfiguration} convention) via Jackson's {@code @JsonAnyGetter} /
 * {@code @JsonAnySetter}. So users write:
 *
 * <pre>{@code
 * servers:
 *   myExt:
 *     type: sample
 *     sample:                # bare key, no `typeSpecificConfig:` wrapper
 *       name: foo
 *       port: 8081
 * }</pre>
 *
 * <p>Background: see {@code doc/adr/canopy-extension-repo-extraction.md}.
 */
class TypeSpecificConfigSpiTest {

  /** Concrete server exposing the protected helper for assertion. */
  static class TestServer extends AbstractTigerServer {
    TestServer(CfgServer cfg) {
      super("test-server", cfg, null);
    }

    @Override
    public void performStartup() {
      // do nothing
    }

    @Override
    public void shutdown() {
      // do nothing
    }

    <T> T read(String key, Class<T> type) {
      return readTypeSpecificConfig(key, type);
    }
  }

  /**
   * Sample extension-side POJO. Lives in this test file deliberately — pretend it ships from an
   * out-of-tree extension jar.
   */
  @Data
  static class SampleExtensionConfig {
    private String name;
    private int port;
    private List<String> tags = List.of();
  }

  @Test
  void readsTypedConfigFromBareTopLevelYamlKey() {
    String yaml =
        """
        type: sample
        sample:
          name: foo
          port: 8081
          tags: [a, b]
        """;
    CfgServer cfg = new YAMLMapper().readValue(yaml, CfgServer.class);

    SampleExtensionConfig out = new TestServer(cfg).read("sample", SampleExtensionConfig.class);

    assertThat(out).isNotNull();
    assertThat(out.getName()).isEqualTo("foo");
    assertThat(out.getPort()).isEqualTo(8081);
    assertThat(out.getTags()).containsExactly("a", "b");
  }

  @Test
  void returnsNullWhenKeyAbsent() {
    SampleExtensionConfig out =
        new TestServer(new CfgServer()).read("sample", SampleExtensionConfig.class);
    assertThat(out).isNull();
  }

  @Test
  void toleratesUnknownFieldsForForwardCompatibility() {
    String yaml =
        """
        type: sample
        sample:
          name: foo
          port: 8081
          futureFieldFromNewerExtension: 42
          anotherUnknown: {nested: true}
        """;
    CfgServer cfg = new YAMLMapper().readValue(yaml, CfgServer.class);

    SampleExtensionConfig out = new TestServer(cfg).read("sample", SampleExtensionConfig.class);

    assertThat(out.getName()).isEqualTo("foo");
    assertThat(out.getPort()).isEqualTo(8081);
  }

  @Test
  void multipleBareKeysAreIndependent() {
    String yaml =
        """
        type: sample
        sample: { name: alpha, port: 1 }
        other:  { name: beta,  port: 2 }
        """;
    CfgServer cfg = new YAMLMapper().readValue(yaml, CfgServer.class);
    TestServer s = new TestServer(cfg);

    assertThat(s.read("sample", SampleExtensionConfig.class).getName()).isEqualTo("alpha");
    assertThat(s.read("other", SampleExtensionConfig.class).getName()).isEqualTo("beta");
  }

  @Test
  void failsWithStartupExceptionWhenValueCannotBeBound() {
    String yaml =
        """
        type: sample
        sample:
          port: not-a-number
        """;
    CfgServer cfg = new YAMLMapper().readValue(yaml, CfgServer.class);

    var testServer = new TestServer(cfg);
    assertThatThrownBy(() -> testServer.read("sample", SampleExtensionConfig.class))
        .isInstanceOf(TigerEnvironmentStartupException.class)
        .hasMessageContaining("typeSpecificConfig['sample']")
        .hasMessageContaining("test-server")
        .hasMessageContaining("SampleExtensionConfig");
  }

  @Test
  void roundTripsThroughJsonWritingBareTopLevelKeys() {
    CfgServer original = new CfgServer();
    original.setType("sample");
    ObjectMapper mapper = new ObjectMapper();
    JsonNode tree = mapper.readTree("{\"name\":\"keep-me\",\"port\":4242}");
    original.setTypeSpecificConfigEntry("sample", tree);

    String json = mapper.writeValueAsString(original);
    // Bare-key emission: "sample" appears at the same level as "type", not under a wrapper.
    assertThat(json)
        .contains("\"type\":\"sample\"")
        .contains("\"sample\":{")
        .doesNotContain("typeSpecificConfig");

    CfgServer restored = mapper.readValue(json, CfgServer.class);
    SampleExtensionConfig bound =
        new TestServer(restored).read("sample", SampleExtensionConfig.class);
    assertThat(bound.getName()).isEqualTo("keep-me");
    assertThat(bound.getPort()).isEqualTo(4242);
  }

  @Test
  void unreadKeysAreReportedAfterReads() {
    String yaml =
        """
        type: sample
        sample: { name: alpha, port: 1 }
        typo:   { something: 0 }
        """;
    CfgServer cfg = new YAMLMapper().readValue(yaml, CfgServer.class);
    TestServer s = new TestServer(cfg);

    // Before any read, both keys are unread.
    assertThat(cfg.getUnreadTypeSpecificConfigKeys()).containsExactly("sample", "typo");

    // Reading 'sample' clears it from the unread set; 'typo' stays unread.
    s.read("sample", SampleExtensionConfig.class);
    assertThat(cfg.getUnreadTypeSpecificConfigKeys()).containsExactly("typo");

    // A read for an absent key still marks it claimed (legitimate "is this key set?" probe),
    // and is harmless because it has no unread entry to begin with.
    s.read("never-set", SampleExtensionConfig.class);
    assertThat(cfg.getUnreadTypeSpecificConfigKeys()).containsExactly("typo");
  }
}
