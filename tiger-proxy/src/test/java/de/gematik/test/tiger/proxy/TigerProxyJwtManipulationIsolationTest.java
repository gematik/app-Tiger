/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 */
package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.proxy.data.JwtManipulationConfiguration;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TigerProxyJwtManipulationIsolationTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /** Verify that one proxy instance's JWT manipulation state does not leak into another instance. */
  @Test
  void applyJwtManipulationIfConfigured_shouldNotLeakAcrossProxyInstances() throws Exception {
    try (TigerProxy manipulatingProxy =
            new TigerProxy(TigerProxyConfiguration.builder().build());
        TigerProxy untouchedProxy = new TigerProxy(TigerProxyConfiguration.builder().build())) {
      manipulatingProxy.configureJwtManipulation(
          JwtManipulationConfiguration.builder()
              .jwtLocation("$.header.authorization")
              .jwtField("body.sub")
              .replaceWith("mutated-sub")
              .build());

      String originalJwt = createUnsignedJwt(Map.of("alg", "none"), Map.of("sub", "original-sub"));
      HttpRequest manipulatedRequest =
          HttpRequest.request("/hello").withHeader("Authorization", "Bearer " + originalJwt);
      HttpRequest untouchedRequest =
          HttpRequest.request("/hello").withHeader("Authorization", "Bearer " + originalJwt);

      manipulatingProxy.applyJwtManipulationIfConfigured(manipulatedRequest);
      untouchedProxy.applyJwtManipulationIfConfigured(untouchedRequest);

      assertThat(extractJwtFromAuthorization(manipulatedRequest))
          .isNotEqualTo(originalJwt)
          .satisfies(jwt -> assertThat(extractBodyClaim(jwt, "sub")).isEqualTo("mutated-sub"));
      assertThat(extractJwtFromAuthorization(untouchedRequest)).isEqualTo(originalJwt);
      assertThat(untouchedProxy.hasActiveJwtManipulation()).isFalse();
    }
  }

  /** Verify that lowercase {@code dpop } Authorization prefixes are manipulated correctly. */
  @Test
  void applyJwtManipulationIfConfigured_shouldHandleLowercaseDpopAuthorizationPrefix()
      throws Exception {
    try (TigerProxy manipulatingProxy = new TigerProxy(TigerProxyConfiguration.builder().build())) {
      manipulatingProxy.configureJwtManipulation(
          JwtManipulationConfiguration.builder()
              .jwtLocation("$.header.authorization")
              .jwtField("body.sub")
              .replaceWith("mutated-sub")
              .build());

      String originalJwt = createUnsignedJwt(Map.of("alg", "none"), Map.of("sub", "original-sub"));
      HttpRequest manipulatedRequest =
          HttpRequest.request("/hello").withHeader("Authorization", "dpop " + originalJwt);

      manipulatingProxy.applyJwtManipulationIfConfigured(manipulatedRequest);

      assertThat(manipulatedRequest.getFirstHeader("Authorization")).startsWith("dpop ");
      assertThat(extractJwtFromAuthorization(manipulatedRequest))
          .isNotEqualTo(originalJwt)
          .satisfies(jwt -> assertThat(extractBodyClaim(jwt, "sub")).isEqualTo("mutated-sub"));
    }
  }

  /** Verify that bracketed DPoP header locations update the DPoP {@code ath} claim. */
  @Test
  void applyJwtManipulationIfConfigured_shouldUpdateAthForBracketedDpopHeaderLocation()
      throws Exception {
    try (TigerProxy manipulatingProxy = new TigerProxy(TigerProxyConfiguration.builder().build())) {
      manipulatingProxy.configureJwtManipulation(
          JwtManipulationConfiguration.builder()
              .jwtLocation("$.header.authorization")
              .jwtField("body.sub")
              .replaceWith("mutated-sub")
              .dpopLocation("$.header.[~'DPoP']")
              .dpopPrivateKeyPem(TEST_EC_PRIVATE_KEY_PEM)
              .updateAth(true)
              .build());

      String originalAccessToken =
          createUnsignedJwt(Map.of("alg", "none"), Map.of("sub", "original-sub"));
      String originalDpopJwt =
          createUnsignedJwt(Map.of("alg", "ES256"), Map.of("ath", "stale-ath"));
      HttpRequest manipulatedRequest =
          HttpRequest.request("/hello")
              .withHeader("Authorization", "Bearer " + originalAccessToken)
              .withHeader("DPoP", originalDpopJwt);

      manipulatingProxy.applyJwtManipulationIfConfigured(manipulatedRequest);

      String manipulatedAccessToken = extractJwtFromAuthorization(manipulatedRequest);
      String manipulatedDpopJwt = extractJwtFromHeaderValue(manipulatedRequest.getFirstHeader("DPoP"));

      assertThat(extractBodyClaim(manipulatedAccessToken, "sub")).isEqualTo("mutated-sub");
      assertThat(extractBodyClaim(manipulatedDpopJwt, "ath"))
          .isEqualTo(calculateAth(manipulatedAccessToken));
    }
  }

  /** Verify that plain dot-path locations preserve literal leading {@code ~} characters. */
  @Test
  void applyJwtManipulationIfConfigured_shouldPreserveLiteralLeadingTildeInHeaderLocation()
      throws Exception {
    try (TigerProxy manipulatingProxy = new TigerProxy(TigerProxyConfiguration.builder().build())) {
      manipulatingProxy.configureJwtManipulation(
          JwtManipulationConfiguration.builder()
              .jwtLocation("$.header.~token")
              .jwtField("body.sub")
              .replaceWith("mutated-sub")
              .build());

      String originalJwt = createUnsignedJwt(Map.of("alg", "none"), Map.of("sub", "original-sub"));
      HttpRequest manipulatedRequest =
          HttpRequest.request("/hello").withHeader("~token", "Bearer " + originalJwt);

      manipulatingProxy.applyJwtManipulationIfConfigured(manipulatedRequest);

      assertThat(extractJwtFromHeaderValue(manipulatedRequest.getFirstHeader("~token")))
          .isNotEqualTo(originalJwt)
          .satisfies(jwt -> assertThat(extractBodyClaim(jwt, "sub")).isEqualTo("mutated-sub"));
    }
  }

  /** Verify that composite {@code &&} conditions still match request path and body checks. */
  @Test
  void applyJwtManipulationIfConfigured_shouldMatchCompositeAndConditions() throws Exception {
    try (TigerProxy manipulatingProxy = new TigerProxy(TigerProxyConfiguration.builder().build())) {
      manipulatingProxy.configureJwtManipulation(
          JwtManipulationConfiguration.builder()
              .jwtLocation("$.header.authorization")
              .jwtField("body.sub")
              .replaceWith("mutated-sub")
              .condition("message.path =~ '/token' && message.body.client_id == 'foo'")
              .build());

      String originalJwt = createUnsignedJwt(Map.of("alg", "none"), Map.of("sub", "original-sub"));
      HttpRequest manipulatedRequest =
          HttpRequest.request("/token")
              .withHeader("Authorization", "Bearer " + originalJwt)
              .withHeader("Content-Type", "application/json")
              .withBody("{\"client_id\":\"foo\"}".getBytes(StandardCharsets.UTF_8));

      manipulatingProxy.applyJwtManipulationIfConfigured(manipulatedRequest);

      assertThat(extractJwtFromAuthorization(manipulatedRequest))
          .isNotEqualTo(originalJwt)
          .satisfies(jwt -> assertThat(extractBodyClaim(jwt, "sub")).isEqualTo("mutated-sub"));
    }
  }

  /** Verify that compact JWT variants are built from the live body JWT. */
  @Test
  void applyJwtManipulationIfConfigured_shouldBuildTwoSegmentVariantFromLiveBodyJwt()
      throws Exception {
    try (TigerProxy manipulatingProxy = new TigerProxy(TigerProxyConfiguration.builder().build())) {
      manipulatingProxy.configureJwtManipulation(
          JwtManipulationConfiguration.builder()
              .jwtLocation("$.body.client_assertion")
              .jwtField("variant.name")
              .replaceWith("two_segments")
              .build());

      String originalJwt = createUnsignedJwt(Map.of("alg", "none"), Map.of("sub", "live-sub"));
      HttpRequest manipulatedRequest = createClientAssertionFormRequest(originalJwt);

      manipulatingProxy.applyJwtManipulationIfConfigured(manipulatedRequest);

      String[] parts = originalJwt.split("\\.", -1);
      assertThat(extractClientAssertion(manipulatedRequest)).isEqualTo(parts[0] + "." + parts[1]);
    }
  }

  /** Verify that nested live JWT variants wrap the current intercepted JWT. */
  @Test
  void applyJwtManipulationIfConfigured_shouldBuildNestedVariantFromLiveBodyJwt()
      throws Exception {
    try (TigerProxy manipulatingProxy = new TigerProxy(TigerProxyConfiguration.builder().build())) {
      manipulatingProxy.configureJwtManipulation(
          JwtManipulationConfiguration.builder()
              .jwtLocation("$.body.client_assertion")
              .jwtField("variant.name")
              .replaceWith("nested_cty_jwt_valid_inner")
              .privateKeyPem(TEST_EC_PRIVATE_KEY_PEM)
              .replaceJwk(true)
              .build());

      String originalJwt = createUnsignedJwt(Map.of("alg", "ES256"), Map.of("sub", "live-sub"));
      HttpRequest manipulatedRequest = createClientAssertionFormRequest(originalJwt);

      manipulatingProxy.applyJwtManipulationIfConfigured(manipulatedRequest);

      String nestedJwt = extractClientAssertion(manipulatedRequest);
      assertThat(extractHeaderClaim(nestedJwt, "cty")).isEqualTo("JWT");
      assertThat(decodeJwtBodyAsText(nestedJwt)).isEqualTo(originalJwt);
      assertThat(decodeJwtHeaderAsText(nestedJwt)).contains("\"jwk\"");
    }
  }

  /** Verify that duplicate {@code alg} variants keep both header members when replacing JWK. */
  @Test
  void applyJwtManipulationIfConfigured_shouldPreserveDuplicateAlgWhenReplacingJwk()
      throws Exception {
    try (TigerProxy manipulatingProxy = new TigerProxy(TigerProxyConfiguration.builder().build())) {
      manipulatingProxy.configureJwtManipulation(
          JwtManipulationConfiguration.builder()
              .jwtLocation("$.body.client_assertion")
              .jwtField("variant.name")
              .replaceWith("duplicate_alg_headers")
              .privateKeyPem(TEST_EC_PRIVATE_KEY_PEM)
              .replaceJwk(true)
              .build());

      String originalJwt = createUnsignedJwt(Map.of("alg", "ES256"), Map.of("sub", "live-sub"));
      HttpRequest manipulatedRequest = createClientAssertionFormRequest(originalJwt);

      manipulatingProxy.applyJwtManipulationIfConfigured(manipulatedRequest);

      String headerJson = decodeJwtHeaderAsText(extractClientAssertion(manipulatedRequest));
      assertThat(headerJson.split("\"alg\"", -1)).hasSize(3);
      assertThat(headerJson).contains("\"jwk\"");
    }
  }

  /** Verify that removing the only JOSE header member still inserts a valid replacement JWK. */
  @Test
  void applyJwtManipulationIfConfigured_shouldReplaceJwkInMissingAlgEmptyHeader()
      throws Exception {
    try (TigerProxy manipulatingProxy = new TigerProxy(TigerProxyConfiguration.builder().build())) {
      manipulatingProxy.configureJwtManipulation(
          JwtManipulationConfiguration.builder()
              .jwtLocation("$.body.client_assertion")
              .jwtField("variant.name")
              .replaceWith("missing_alg")
              .privateKeyPem(TEST_EC_PRIVATE_KEY_PEM)
              .replaceJwk(true)
              .build());

      String originalJwt = createUnsignedJwt(Map.of("alg", "ES256"), Map.of("sub", "live-sub"));
      HttpRequest manipulatedRequest = createClientAssertionFormRequest(originalJwt);

      manipulatingProxy.applyJwtManipulationIfConfigured(manipulatedRequest);

      var header = OBJECT_MAPPER.readTree(decodeJwtHeaderAsText(
          extractClientAssertion(manipulatedRequest)));
      assertThat(header.has("alg")).isFalse();
      assertThat(header.has("jwk")).isTrue();
    }
  }

  private static final String TEST_EC_PRIVATE_KEY_PEM =
      """
      -----BEGIN PRIVATE KEY-----
      MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCDm0+zJJrMu9MCs/yPY
      d4iNEB2M6R/VJDe5EWcYG4iohw==
      -----END PRIVATE KEY-----
      """;

  /** Create a compact unsigned JWT for focused header/body manipulation tests. */
  private static String createUnsignedJwt(Map<String, Object> header, Map<String, Object> body)
      throws Exception {
    String headerB64 =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(OBJECT_MAPPER.writeValueAsBytes(header));
    String bodyB64 =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(OBJECT_MAPPER.writeValueAsBytes(body));
    String signatureB64 =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("sig".getBytes(StandardCharsets.UTF_8));
    return headerB64 + "." + bodyB64 + "." + signatureB64;
  }

  /** Create a form request containing a client_assertion JWT parameter. */
  private static HttpRequest createClientAssertionFormRequest(String jwt) {
    String formBody = "client_id=live-client&client_assertion="
        + URLEncoder.encode(jwt, StandardCharsets.UTF_8);
    return HttpRequest.request("/token")
        .withHeader("Content-Type", "application/x-www-form-urlencoded")
        .withBody(formBody.getBytes(StandardCharsets.UTF_8));
  }

  /** Extract the decoded client_assertion form parameter from a request. */
  private static String extractClientAssertion(HttpRequest request) {
    String body = new String(request.getBody(), StandardCharsets.UTF_8);
    for (String parameter : body.split("&")) {
      String[] parts = parameter.split("=", 2);
      if (parts.length == 2 && "client_assertion".equals(parts[0])) {
        return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
      }
    }
    throw new AssertionError("client_assertion parameter not found");
  }

  /** Extract the compact JWT portion from an Authorization header value. */
  private static String extractJwtFromAuthorization(HttpRequest request) {
    return extractJwtFromHeaderValue(request.getFirstHeader("Authorization"));
  }

  /** Extract the compact JWT portion from a header value with an optional auth scheme prefix. */
  private static String extractJwtFromHeaderValue(String headerValue) {
    int firstWhitespace = headerValue.indexOf(' ');
    return firstWhitespace >= 0 ? headerValue.substring(firstWhitespace + 1) : headerValue;
  }

  /** Read a string claim from the JWT body for assertions. */
  private static String extractBodyClaim(String jwt, String claim) throws Exception {
    String[] parts = jwt.split("\\.");
    String bodyJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    return OBJECT_MAPPER.readTree(bodyJson).get(claim).asText();
  }

  /** Read a string claim from the JWT header for assertions. */
  private static String extractHeaderClaim(String jwt, String claim) throws Exception {
    return OBJECT_MAPPER.readTree(decodeJwtHeaderAsText(jwt)).get(claim).asText();
  }

  /** Decode the JWT header segment as UTF-8 text. */
  private static String decodeJwtHeaderAsText(String jwt) {
    String[] parts = jwt.split("\\.", -1);
    return new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
  }

  /** Decode the JWT body segment as UTF-8 text. */
  private static String decodeJwtBodyAsText(String jwt) {
    String[] parts = jwt.split("\\.", -1);
    return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
  }

  /** Calculate the DPoP {@code ath} value for the given compact access token. */
  private static String calculateAth(String accessToken) throws Exception {
    byte[] hash =
        MessageDigest.getInstance("SHA-256").digest(accessToken.getBytes(StandardCharsets.US_ASCII));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
  }
}
