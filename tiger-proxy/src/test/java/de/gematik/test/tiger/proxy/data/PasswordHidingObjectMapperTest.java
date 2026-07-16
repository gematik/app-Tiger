/*
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */

package de.gematik.test.tiger.proxy.data;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.data.config.tigerproxy.ForwardProxyInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerRouteAuthenticationConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerTlsConfiguration;
import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity;
import de.gematik.test.tiger.common.pki.TigerPkiIdentityInformation;
import de.gematik.test.tiger.common.pki.TigerPkiIdentityLoader.StoreType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PasswordHidingObjectMapperTest {

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = PasswordHidingObjectMapper.createObjectMapper();
  }

  @Test
  void hidesForwardProxyPassword() {
    JsonNode serialized = serialize(createConfigurationWithForwardProxy());

    assertThat(serialized.path("forwardToProxy").has("password")).isFalse();
    assertThat(serialized.path("forwardToProxy").path("username").asString()).isEqualTo("user");
    assertThat(serialized.path("forwardToProxy").path("hostname").asString())
        .isEqualTo("proxy.example");
    assertThat(serialized.path("forwardToProxy").path("port").asInt()).isEqualTo(3128);
  }

  @Test
  void hidesRouteAuthenticationPasswordAndBearerToken() {
    JsonNode serialized = serialize(createConfigurationWithRouteAuthentication());
    assertThat(serialized.path("proxyRoutes").path(0).path("authentication").has("password"))
        .isFalse();
    assertThat(serialized.path("proxyRoutes").path(0).path("authentication").has("bearerToken"))
        .isFalse();
    assertThat(
            serialized
                .path("proxyRoutes")
                .path(0)
                .path("authentication")
                .path("username")
                .asString())
        .isEqualTo("route-user");
  }

  private TigerProxyConfiguration createConfigurationWithForwardProxy() {
    ForwardProxyInfo forwardProxy =
        ForwardProxyInfo.builder()
            .hostname("proxy.example")
            .port(3128)
            .username("user")
            .password("super-secret-password")
            .build();

    return TigerProxyConfiguration.builder().forwardToProxy(forwardProxy).build();
  }

  private TigerProxyConfiguration createConfigurationWithRouteAuthentication() {
    TigerRouteAuthenticationConfiguration authentication =
        TigerRouteAuthenticationConfiguration.builder()
            .username("route-user")
            .password("route-password")
            .bearerToken("route-bearer-token")
            .build();

    TigerConfigurationRoute route =
        TigerConfigurationRoute.builder()
            .from("http://example.from")
            .to("http://example.to")
            .authentication(authentication)
            .build();

    return TigerProxyConfiguration.builder().proxyRoutes(List.of(route)).build();
  }

  @Test
  void hidesPkiPasswordInObjectFormatIdentity() {
    JsonNode tls = serializeTls(tlsWithForwardMutualIdentity(objectFormatIdentity()));

    assertThat(tls.path("forwardMutualTlsIdentity").path("filenames").path(0).asString())
        .isEqualTo("idpSig.p12");
    assertThat(tls.path("forwardMutualTlsIdentity").path("alias").asString()).isEqualTo("blerb");
    assertThat(tls.path("forwardMutualTlsIdentity").has("password")).isFalse();
  }

  @Test
  void hidesPkiPasswordInCompactFormat() {
    JsonNode tls = serializeTls(tlsWithServerIdentity(compactFormatIdentity()));

    assertThat(tls.path("serverIdentity").isString()).isTrue();
    assertThat(tls.path("serverIdentity").asString()).doesNotContain("srv-secret");
    assertThat(tls.path("serverIdentity").has("password")).isFalse();
  }

  /**
   * Object format: explicit filename/alias/password/storeType fields. Uses no-arg constructor +
   * setter to avoid file loading in the test.
   */
  private TigerConfigurationPkiIdentity objectFormatIdentity() {
    TigerConfigurationPkiIdentity identity = new TigerConfigurationPkiIdentity();
    identity.setFileLoadingInformation(
        TigerPkiIdentityInformation.builder()
            .filenames(List.of("idpSig.p12"))
            .alias("blerb")
            .password("00")
            .storeType(StoreType.PKCS12)
            .build());
    return identity;
  }

  /**
   * Compact string format: "filename;secret" serialized as a single text value. Uses no-arg
   * constructor + setter to avoid file loading in the test.
   */
  private TigerConfigurationPkiIdentity compactFormatIdentity() {
    TigerConfigurationPkiIdentity identity = new TigerConfigurationPkiIdentity();
    identity.setFileLoadingInformation(
        TigerPkiIdentityInformation.builder()
            .filenames(List.of("idpSig.p12"))
            .password("srv-secret1")
            .aliasesOrPasswords(List.of("srv-secret2"))
            .useCompactFormat(true)
            .storeType(StoreType.PKCS12)
            .build());
    return identity;
  }

  private TigerTlsConfiguration tlsWithForwardMutualIdentity(
      TigerConfigurationPkiIdentity identity) {
    return TigerTlsConfiguration.builder().forwardMutualTlsIdentity(identity).build();
  }

  private TigerTlsConfiguration tlsWithServerIdentity(TigerConfigurationPkiIdentity identity) {
    return TigerTlsConfiguration.builder().serverIdentity(identity).build();
  }

  private JsonNode serializeTls(TigerTlsConfiguration tls) {
    return mapper.readTree(mapper.writeValueAsString(tls));
  }

  private JsonNode serialize(TigerProxyConfiguration config) {
    return mapper.readTree(mapper.writeValueAsString(config));
  }
}
