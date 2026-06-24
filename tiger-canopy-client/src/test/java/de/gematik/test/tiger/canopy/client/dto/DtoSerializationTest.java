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
package de.gematik.test.tiger.canopy.client.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.gematik.test.tiger.canopy.client.config.ControlMode;
import de.gematik.test.tiger.canopy.client.config.MatchType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for the CANOPY wire-format DTOs. Mirrors the {@link ObjectMapper} configuration used by
 * {@code CanopyAdminClient} so we exercise the exact serialization contract.
 */
class DtoSerializationTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .setSerializationInclusion(JsonInclude.Include.NON_NULL)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  // ---- AddProxiedHostRequest ----------------------------------------

  @Test
  void addProxiedHostRequest_twoArgCtor_setsTigerProxyUrlNull() {
    var r = new AddProxiedHostRequest("api.example.com", MatchType.EXACT);

    assertThat(r.host()).isEqualTo("api.example.com");
    assertThat(r.matchType()).isEqualTo(MatchType.EXACT);
    assertThat(r.tigerProxyUrl()).isNull();
  }

  @Test
  void addProxiedHostRequest_threeArgCtor_keepsOverride() {
    var r = new AddProxiedHostRequest("api.example.com", MatchType.SUFFIX, "http://p:9090");

    assertThat(r.tigerProxyUrl()).isEqualTo("http://p:9090");
  }

  @Test
  void addProxiedHostRequest_serialize_omitsNullProxyUrl() throws Exception {
    var json = MAPPER.writeValueAsString(new AddProxiedHostRequest("a.example", MatchType.EXACT));

    var node = MAPPER.readTree(json);
    assertThat(node.get("host").asText()).isEqualTo("a.example");
    assertThat(node.get("matchType").asText()).isEqualTo("EXACT");
    assertThat(node.has("tigerProxyUrl")).isFalse();
  }

  @Test
  void addProxiedHostRequest_serialize_includesNonNullProxyUrl() throws Exception {
    var json =
        MAPPER.writeValueAsString(
            new AddProxiedHostRequest("a.example", MatchType.SUFFIX, "http://p:9090"));

    var node = MAPPER.readTree(json);
    assertThat(node.get("tigerProxyUrl").asText()).isEqualTo("http://p:9090");
    assertThat(node.get("matchType").asText()).isEqualTo("SUFFIX");
  }

  @Test
  void addProxiedHostRequest_deserialize_acceptsMissingMatchTypeAndProxyUrl() throws Exception {
    var r = MAPPER.readValue("{\"host\":\"a.example\"}", AddProxiedHostRequest.class);

    assertThat(r.host()).isEqualTo("a.example");
    assertThat(r.matchType()).isNull();
    assertThat(r.tigerProxyUrl()).isNull();
  }

  @Test
  void addProxiedHostRequest_deserialize_ignoresUnknownFields() throws Exception {
    var r =
        MAPPER.readValue(
            "{\"host\":\"a.example\",\"matchType\":\"EXACT\",\"future\":42}",
            AddProxiedHostRequest.class);

    assertThat(r.host()).isEqualTo("a.example");
  }

  @Test
  void addProxiedHostRequest_deserialize_rejectsUnknownEnumValue() {
    assertThatThrownBy(
            () ->
                MAPPER.readValue(
                    "{\"host\":\"a.example\",\"matchType\":\"REGEX\"}",
                    AddProxiedHostRequest.class))
        .isInstanceOf(Exception.class);
  }

  // ---- ProxiedHostDto ------------------------------------------------

  @Test
  void proxiedHostDto_fourArgCtor_setsTigerProxyUrlNull() {
    var dto =
        new ProxiedHostDto(
            "a.example", MatchType.EXACT, Instant.parse("2026-01-01T00:00:00Z"), "r-1");

    assertThat(dto.tigerProxyUrl()).isNull();
    assertThat(dto.routeId()).isEqualTo("r-1");
  }

  @Test
  void proxiedHostDto_roundTripsInstantAsIso8601() throws Exception {
    var dto =
        new ProxiedHostDto(
            "a.example",
            MatchType.SUFFIX,
            Instant.parse("2026-05-07T12:00:00Z"),
            "r-1",
            "http://p:9090");

    var json = MAPPER.writeValueAsString(dto);
    JsonNode node = MAPPER.readTree(json);
    assertThat(node.get("addedAt").asText()).isEqualTo("2026-05-07T12:00:00Z");

    var back = MAPPER.readValue(json, ProxiedHostDto.class);
    assertThat(back).isEqualTo(dto);
  }

  @Test
  void proxiedHostDto_omitsNullsWhenSerialized() throws Exception {
    var dto = new ProxiedHostDto("a.example", MatchType.EXACT, null, null, null);

    var node = MAPPER.readTree(MAPPER.writeValueAsString(dto));
    assertThat(node.has("addedAt")).isFalse();
    assertThat(node.has("routeId")).isFalse();
    assertThat(node.has("tigerProxyUrl")).isFalse();
    assertThat(node.get("host").asText()).isEqualTo("a.example");
  }

  // ---- BulkAddRequest / BulkAddResponse ------------------------------

  @Test
  void bulkAddRequest_roundTrip() throws Exception {
    var req =
        new BulkAddRequest(
            List.of(
                new AddProxiedHostRequest("a.example", MatchType.EXACT),
                new AddProxiedHostRequest("b.example", MatchType.SUFFIX, "http://p:9090")));

    var json = MAPPER.writeValueAsString(req);
    var back = MAPPER.readValue(json, BulkAddRequest.class);

    assertThat(back.hosts()).hasSize(2);
    assertThat(back.hosts().get(0).matchType()).isEqualTo(MatchType.EXACT);
    assertThat(back.hosts().get(1).tigerProxyUrl()).isEqualTo("http://p:9090");
  }

  @Test
  void bulkAddResponse_deserialize() throws Exception {
    var resp =
        MAPPER.readValue(
            """
            {
              "added":[{"host":"a.example","matchType":"EXACT"}],
              "unchanged":[{"host":"b.example","matchType":"SUFFIX"}]
            }""",
            BulkAddResponse.class);

    assertThat(resp.added()).hasSize(1);
    assertThat(resp.added().get(0).host()).isEqualTo("a.example");
    assertThat(resp.unchanged()).hasSize(1);
    assertThat(resp.unchanged().get(0).matchType()).isEqualTo(MatchType.SUFFIX);
  }

  @Test
  void bulkAddResponse_acceptsEmptyLists() throws Exception {
    var resp = MAPPER.readValue("{\"added\":[],\"unchanged\":[]}", BulkAddResponse.class);

    assertThat(resp.added()).isEmpty();
    assertThat(resp.unchanged()).isEmpty();
  }

  // ---- ConfigDto -----------------------------------------------------

  @Test
  void configDto_roundTrip() throws Exception {
    var cfg = new ConfigDto("http://proxy:9090", ControlMode.ROUTE_PER_HOST, 53);

    var json = MAPPER.writeValueAsString(cfg);
    assertThat(MAPPER.readValue(json, ConfigDto.class)).isEqualTo(cfg);
  }

  @Test
  void configDto_deserialize_allControlModes() throws Exception {
    for (ControlMode mode : ControlMode.values()) {
      var json = "{\"tigerProxyUrl\":\"x\",\"controlMode\":\"" + mode.name() + "\",\"dnsPort\":1}";
      assertThat(MAPPER.readValue(json, ConfigDto.class).controlMode()).isEqualTo(mode);
    }
  }

  // ---- UpdateProxyUrlRequest ----------------------------------------

  @Test
  void updateProxyUrlRequest_roundTrip() throws Exception {
    var req = new UpdateProxyUrlRequest("http://new-proxy:9090");

    var json = MAPPER.writeValueAsString(req);
    assertThat(MAPPER.readTree(json).get("url").asText()).isEqualTo("http://new-proxy:9090");
    assertThat(MAPPER.readValue(json, UpdateProxyUrlRequest.class)).isEqualTo(req);
  }

  // ---- ApiErrorResponse ---------------------------------------------

  @Test
  void apiErrorResponse_factorySetsTimestamp_andCarriesStatusMessage() {
    var before = Instant.now().minusSeconds(1);
    var err = ApiErrorResponse.of(404, "not found");
    var after = Instant.now().plusSeconds(1);

    assertThat(err.status()).isEqualTo(404);
    assertThat(err.message()).isEqualTo("not found");
    assertThat(err.timestamp()).isBetween(before, after);
  }

  @Test
  void apiErrorResponse_roundTrip() throws Exception {
    var err = new ApiErrorResponse(Instant.parse("2026-05-07T12:00:00Z"), 500, "boom");

    var json = MAPPER.writeValueAsString(err);
    assertThat(MAPPER.readTree(json).get("timestamp").asText()).isEqualTo("2026-05-07T12:00:00Z");
    assertThat(MAPPER.readValue(json, ApiErrorResponse.class)).isEqualTo(err);
  }
}
