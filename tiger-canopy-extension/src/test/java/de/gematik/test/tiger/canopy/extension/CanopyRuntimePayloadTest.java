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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the {@code SPRING_APPLICATION_JSON} envelope forwarded into the canopy container is
 * trimmed to runtime fields only (no Docker knobs) and stays parseable under a strict mapper that
 * would reject unknown properties on the canopy side.
 *
 * <p>This is the round-trip guard called out in the follow-up plan, item 5. We do not depend on the
 * {@code tiger-canopy} module from this extension (it's the runtime container payload, not a
 * Tiger-side library), so the "deserialises cleanly on canopy" check is done structurally:
 * field-name parity with the documented {@code CanopyConfiguration.*} bind keys, plus a
 * FAIL_ON_UNKNOWN_PROPERTIES round-trip into a local mirror.
 */
class CanopyRuntimePayloadTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * The exact runtime field set canopy's {@code CanopyConfiguration} binds (as of 2026-05). If
   * canopy adds a field, this test will fail until the trimmed envelope is extended.
   */
  private static final Set<String> CANOPY_RUNTIME_FIELDS =
      Set.of(
          "dnsPort",
          "tigerProxyUrl",
          "proxiedHosts",
          "controlMode",
          "upstreamDnsServers",
          "defaultTtlSeconds",
          "proxyClientHttpVersion");

  /**
   * Local mirror of canopy's runtime POJO — kept tiny so this test doesn't depend on tiger-canopy.
   */
  static final class CanopyConfigMirror {
    public int dnsPort;
    public String tigerProxyUrl;
    public List<ProxiedHost> proxiedHosts;
    public String controlMode;
    public List<String> upstreamDnsServers;
    public int defaultTtlSeconds;
    public String proxyClientHttpVersion;

    static final class ProxiedHost {
      public String host;
      public String matchType;
    }
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void envelope_containsOnlyRuntimeFields_noDockerKnobs(TigerTestEnvMgr mgr) throws Exception {
    String json =
        """
        {
          "image": "my/tiger-canopy:1.0",
          "adminPort": 12345,
          "dnsHostPort": 54321,
          "networks": ["net1"],
          "extraEnv": {"FOO": "BAR"},
          "tigerProxyUrl": "http://tp:9090",
          "controlMode": "ROUTE_PER_HOST",
          "dnsPort": 5353,
          "defaultTtlSeconds": 120,
          "proxyClientHttpVersion": "HTTP_1_1",
          "upstreamDnsServers": ["8.8.8.8"],
          "proxiedHosts": [{"host": "api.example.com", "matchType": "SUFFIX"}]
        }
        """;
    CfgServer cfg = new CfgServer().setType("canopy").setHostname("canopyunit");
    cfg.setTypeSpecificConfigEntry("canopy", MAPPER.readTree(json));
    CanopyServer s = new CanopyServer("c1", cfg, mgr);
    s.prepareDependencies();
    s.assertThatConfigurationIsCorrect();

    String saj = s.buildSpringApplicationJson();
    JsonNode canopyNode = MAPPER.readTree(saj).get("canopy");

    // Docker-only knobs MUST NOT leak into the forwarded payload.
    assertThat(canopyNode.has("image")).isFalse();
    assertThat(canopyNode.has("adminPort")).isFalse();
    assertThat(canopyNode.has("dnsHostPort")).isFalse();
    assertThat(canopyNode.has("networks")).isFalse();
    assertThat(canopyNode.has("extraEnv")).isFalse();

    // The forwarded key set must match canopy's runtime bind surface exactly.
    Set<String> fieldsPresent = new HashSet<>();
    canopyNode.fieldNames().forEachRemaining(fieldsPresent::add);
    assertThat(fieldsPresent).isEqualTo(CANOPY_RUNTIME_FIELDS);

    // And the user values are forwarded.
    assertThat(canopyNode.get("tigerProxyUrl").asText()).isEqualTo("http://tp:9090");
    assertThat(canopyNode.get("controlMode").asText()).isEqualTo("ROUTE_PER_HOST");
    assertThat(canopyNode.get("dnsPort").asInt()).isEqualTo(5353);
    assertThat(canopyNode.get("defaultTtlSeconds").asInt()).isEqualTo(120);
    assertThat(canopyNode.get("proxyClientHttpVersion").asText()).isEqualTo("HTTP_1_1");
    assertThat(canopyNode.get("upstreamDnsServers").get(0).asText()).isEqualTo("8.8.8.8");
    assertThat(canopyNode.get("proxiedHosts").get(0).get("host").asText())
        .isEqualTo("api.example.com");
    assertThat(canopyNode.get("proxiedHosts").get(0).get("matchType").asText()).isEqualTo("SUFFIX");
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void envelope_roundTripsThroughStrictMapper(TigerTestEnvMgr mgr) throws Exception {
    // FAIL_ON_UNKNOWN_PROPERTIES simulates a future tightening of canopy's config binding. The
    // trimmed envelope must parse cleanly under those rules. (The Tiger-side
    // TigerCanopyConfiguration
    // is *not* strict on purpose — see TigerCanopyConfigurationTest — but the wire format we send
    // to canopy must be.)
    ObjectMapper strict =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    String json =
        """
        {
          "tigerProxyUrl": "http://tp:9090",
          "controlMode": "ROUTE_PER_HOST",
          "dnsPort": 5353,
          "defaultTtlSeconds": 120,
          "proxyClientHttpVersion": "HTTP_2",
          "upstreamDnsServers": ["8.8.8.8", "1.1.1.1"],
          "proxiedHosts": [{"host": "api.example.com", "matchType": "SUFFIX"}]
        }
        """;
    CfgServer cfg = new CfgServer().setType("canopy").setHostname("canopyunit");
    cfg.setTypeSpecificConfigEntry("canopy", MAPPER.readTree(json));
    CanopyServer s = new CanopyServer("c1", cfg, mgr);
    s.prepareDependencies();
    s.assertThatConfigurationIsCorrect();

    String saj = s.buildSpringApplicationJson();
    JsonNode canopyNode = MAPPER.readTree(saj).get("canopy");

    CanopyConfigMirror mirror = strict.treeToValue(canopyNode, CanopyConfigMirror.class);

    assertThat(mirror.tigerProxyUrl).isEqualTo("http://tp:9090");
    assertThat(mirror.controlMode).isEqualTo("ROUTE_PER_HOST");
    assertThat(mirror.dnsPort).isEqualTo(5353);
    assertThat(mirror.defaultTtlSeconds).isEqualTo(120);
    assertThat(mirror.proxyClientHttpVersion).isEqualTo("HTTP_2");
    assertThat(mirror.upstreamDnsServers).containsExactly("8.8.8.8", "1.1.1.1");
    assertThat(mirror.proxiedHosts).hasSize(1);
    assertThat(mirror.proxiedHosts.get(0).host).isEqualTo("api.example.com");
    assertThat(mirror.proxiedHosts.get(0).matchType).isEqualTo("SUFFIX");
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void envelope_emptyConfigForwardsCanopyDefaults(TigerTestEnvMgr mgr) throws Exception {
    CfgServer cfg = new CfgServer().setType("canopy").setHostname("canopyunit");
    CanopyServer s = new CanopyServer("c1", cfg, mgr);
    s.prepareDependencies();
    s.assertThatConfigurationIsCorrect();

    String saj = s.buildSpringApplicationJson();
    JsonNode canopyNode = MAPPER.readTree(saj).get("canopy");

    assertThat(canopyNode.get("dnsPort").asInt()).isEqualTo(53);
    assertThat(canopyNode.get("controlMode").asText()).isEqualTo("NONE");
    assertThat(canopyNode.get("defaultTtlSeconds").asInt()).isEqualTo(30);
    assertThat(canopyNode.get("proxyClientHttpVersion").asText()).isEqualTo("AUTO");
    assertThat(canopyNode.get("proxiedHosts")).isEmpty();
    assertThat(canopyNode.get("upstreamDnsServers")).isEmpty();
    // tigerProxyUrl unset → null and excluded by NON_NULL inclusion.
    assertThat(canopyNode.has("tigerProxyUrl")).isFalse();
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void envelope_perHostTigerProxyUrlOverrideIsForwarded(TigerTestEnvMgr mgr) throws Exception {
    // Per-entry override → key appears in that host's map. Entries without an override get no
    // tigerProxyUrl key at all (NON_NULL inclusion + explicit skip in CanopyRuntimePayload.from()).
    String json =
        """
        {
          "tigerProxyUrl": "http://default-tp:9090",
          "proxiedHosts": [
            { "host": "api.example.com" },
            { "host": "pop3.example.com", "tigerProxyUrl": "http://pop3-tp:9100" }
          ]
        }
        """;
    CfgServer cfg = new CfgServer().setType("canopy").setHostname("canopyunit");
    cfg.setTypeSpecificConfigEntry("canopy", MAPPER.readTree(json));
    CanopyServer s = new CanopyServer("c1", cfg, mgr);
    s.prepareDependencies();
    s.assertThatConfigurationIsCorrect();

    String saj = s.buildSpringApplicationJson();
    JsonNode hosts = MAPPER.readTree(saj).get("canopy").get("proxiedHosts");

    assertThat(hosts.get(0).get("host").asText()).isEqualTo("api.example.com");
    assertThat(hosts.get(0).has("tigerProxyUrl"))
        .as("entry without override must not carry the key")
        .isFalse();

    assertThat(hosts.get(1).get("host").asText()).isEqualTo("pop3.example.com");
    assertThat(hosts.get(1).get("tigerProxyUrl").asText()).isEqualTo("http://pop3-tp:9100");
  }
}
