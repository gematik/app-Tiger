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

import de.gematik.test.tiger.canopy.client.config.ControlMode;
import de.gematik.test.tiger.canopy.client.config.HttpVersion;
import de.gematik.test.tiger.canopy.client.config.MatchType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Round-trip serialization tests for {@link TigerCanopyConfiguration}. The YAML-bound POJO must
 * survive a JSON/YAML round-trip without losing fields, default values, or enum identities — so
 * that a {@code tiger.yaml} written today can be read by tomorrow's {@code CanopyServer} unchanged.
 */
class TigerCanopyConfigurationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void defaultsRoundTripThroughJson() {
    TigerCanopyConfiguration original = new TigerCanopyConfiguration();

    String json = mapper.writeValueAsString(original);
    TigerCanopyConfiguration restored = mapper.readValue(json, TigerCanopyConfiguration.class);

    assertThat(restored).isEqualTo(original);
    assertThat(restored.getDnsPort()).isEqualTo(53);
    assertThat(restored.getControlMode()).isEqualTo(ControlMode.NONE);
    assertThat(restored.getProxyClientHttpVersion()).isEqualTo(HttpVersion.AUTO);
    assertThat(restored.getDefaultTtlSeconds()).isEqualTo(30);
    assertThat(restored.getAdminPort()).isZero();
    assertThat(restored.getDnsHostPort()).isZero();
    assertThat(restored.getProxiedHosts()).isEmpty();
    assertThat(restored.getUpstreamDnsServers()).isEmpty();
    assertThat(restored.getNetworks()).isEmpty();
    assertThat(restored.getExtraEnv()).isEmpty();
    assertThat(restored.getImage()).isNull();
    assertThat(restored.getTigerProxyUrl()).isNull();
  }

  @Test
  void fullyPopulatedRoundTripPreservesAllFields() {
    TigerCanopyConfiguration original = new TigerCanopyConfiguration();
    original.setDnsPort(5353);
    original.setTigerProxyUrl("http://tiger-proxy:8080");
    original.setControlMode(ControlMode.ROUTE_PER_HOST);
    original.setUpstreamDnsServers(List.of("1.1.1.1", "9.9.9.9"));
    original.setDefaultTtlSeconds(60);
    original.setProxyClientHttpVersion(HttpVersion.HTTP_1_1);
    original.setImage("my.registry/tiger-canopy:1.2.3");
    original.setAdminPort(55102);
    original.setDnsHostPort(55153);
    original.setNetworks(List.of("test-net", "shared-net"));

    Map<String, String> env = new LinkedHashMap<>();
    env.put("LOG_LEVEL", "DEBUG");
    env.put("FOO", "bar");
    original.setExtraEnv(env);

    TigerCanopyConfiguration.ProxiedHost h1 = new TigerCanopyConfiguration.ProxiedHost();
    h1.setHost("api.example.com");
    h1.setMatchType(MatchType.EXACT);
    TigerCanopyConfiguration.ProxiedHost h2 = new TigerCanopyConfiguration.ProxiedHost();
    h2.setHost("example.org");
    h2.setMatchType(MatchType.SUFFIX);
    original.setProxiedHosts(List.of(h1, h2));

    String json = mapper.writeValueAsString(original);
    TigerCanopyConfiguration restored = mapper.readValue(json, TigerCanopyConfiguration.class);

    assertThat(restored).isEqualTo(original);
  }

  @Test
  void enumsSerializeAsBareNamesMatchingTheCanopyWireFormat() {
    TigerCanopyConfiguration cfg = new TigerCanopyConfiguration();
    cfg.setControlMode(ControlMode.ROUTE_PER_HOST);
    cfg.setProxyClientHttpVersion(HttpVersion.HTTP_2);
    TigerCanopyConfiguration.ProxiedHost h = new TigerCanopyConfiguration.ProxiedHost();
    h.setHost("a.b");
    h.setMatchType(MatchType.SUFFIX);
    cfg.setProxiedHosts(List.of(h));

    String json = mapper.writeValueAsString(cfg);

    assertThat(json)
        .contains("\"controlMode\":\"ROUTE_PER_HOST\"")
        .contains("\"proxyClientHttpVersion\":\"HTTP_2\"")
        .contains("\"matchType\":\"SUFFIX\"");
  }

  @Test
  void unknownFieldsAreToleratedSoNewerCanopyConfigsLoadOnOlderTiger() {
    String json =
        "{\"dnsPort\":5353,\"someFieldFromTheFuture\":42,"
            + "\"controlMode\":\"NONE\",\"image\":\"x\"}";

    ObjectMapper tolerant = JsonMapper.builder().build();

    TigerCanopyConfiguration restored = tolerant.readValue(json, TigerCanopyConfiguration.class);
    assertThat(restored.getDnsPort()).isEqualTo(5353);
    assertThat(restored.getImage()).isEqualTo("x");
  }
}
