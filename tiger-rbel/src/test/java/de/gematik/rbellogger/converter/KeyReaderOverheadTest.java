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
package de.gematik.rbellogger.converter;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelBinaryFacet;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.facets.jackson.RbelJsonFacet;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KeyReaderOverheadTest {

  private static final String JWK =
      "{\"kty\":\"oct\",\"kid\":\"overhead-test-key\",\"k\":\"c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0\"}";

  private static byte[] response(String jsonBody) {
    return ("HTTP/1.1 200 OK\r\nHost: shop.example.com\r\nX-Request-Id: 5c4f1e9a\r\n"
            + "Content-Type: application/json\r\nContent-Length: %d\r\n\r\n%s"
                .formatted(jsonBody.length(), jsonBody))
        .getBytes(UTF_8);
  }

  private static String signedWith(JsonWebSignature jws, java.security.PrivateKey key)
      throws Exception {
    jws.setKey(key);
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    return jws.getCompactSerialization();
  }

  private static Optional<String> verifiedUsing(RbelElement message) {
    return message.findElement("$.signature.verifiedUsing").orElseThrow().seekValue(String.class);
  }

  private static void collectDocumentRoots(RbelElement element, List<RbelElement> into) {
    if (element.hasFacet(RbelJsonFacet.class) && element.hasFacet(RbelRootFacet.class)) {
      into.add(element);
    }
    element.getChildNodes().forEach(child -> collectDocumentRoots(child, into));
  }

  @Test
  void aJwtThatCarriesItsOwnKeySetIsVerifiedWithIt() throws Exception {
    RsaJsonWebKey key = RsaJwkGenerator.generateJwk(2048);
    key.setKeyId("entity-key");
    var jws = new JsonWebSignature();
    jws.setPayload(
        "{\"jwks\":{\"keys\":[" + key.toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY) + "]}}");

    var message =
        RbelLogger.build()
            .getRbelConverter()
            .convertElement(signedWith(jws, key.getPrivateKey()).getBytes(UTF_8), null);

    assertThat(message.findElement("$.signature.isValid").orElseThrow().seekValue(Boolean.class))
        .contains(true);
    assertThat(verifiedUsing(message)).contains("entity-key");
  }

  @Test
  void aJwtWithAnX5cHeaderIsVerifiedWithTheKeyRegisteredUnderItsKid() throws Exception {
    KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    var subject = new X500Name("CN=x5c-test");
    var now = System.currentTimeMillis();
    X509Certificate certificate =
        new JcaX509CertificateConverter()
            .getCertificate(
                new JcaX509v3CertificateBuilder(
                        subject,
                        BigInteger.ONE,
                        new Date(now - 86_400_000L),
                        new Date(now + 86_400_000L),
                        subject,
                        pair.getPublic())
                    .build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
    var jws = new JsonWebSignature();
    jws.setPayload("{\"a\":1}");
    jws.setCertificateChainHeaderValue(certificate);
    jws.setKeyIdHeaderValue("my-kid");

    var message =
        RbelLogger.build()
            .getRbelConverter()
            .convertElement(signedWith(jws, pair.getPrivate()).getBytes(UTF_8), null);

    assertThat(message.findElement("$.signature.isValid").orElseThrow().seekValue(Boolean.class))
        .contains(true);
    assertThat(verifiedUsing(message)).contains("my-kid");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"keys\":[{\"a\":1}],\"x\":{\"keys\":[],\"y\":[{\"keys\":\"z\"}]}}",
        "{\"jwt\":\"eyJhbGciOiJIUzI1NiJ9.eyJrZXlzIjpbMV19.c2ln\",\"keys\":{\"keys\":1}}",
        "<root><keys><k>1</k></keys><a><keys>2</keys></a></root>",
        "{\"nothing\":[1,2,{\"here\":true}]}",
      })
  void theSearchesOfTheMessageAndOfItsNestedDocumentsTogetherFindWhatTheRbelPathFinds(String body) {
    var logger = RbelLogger.build();
    var message = logger.getRbelConverter().convertElement(response(body), null);
    var searched = new ArrayList<RbelElement>(List.of(message));
    collectDocumentRoots(message, searched);

    assertThat(
            searched.stream()
                .flatMap(
                    root ->
                        root
                            .findAllInTreeWithKey(
                                "keys", element -> !RbelJsonFacet.isRootOfDocument(element))
                            .stream()))
        .containsExactlyInAnyOrderElementsOf(
            new LinkedHashSet<>(message.findRbelPathMembers("$..keys")));
  }

  @Test
  void theSearchOfADocumentLeavesTheDocumentsNestedInItToTheirOwnSearch() {
    var logger = RbelLogger.build();
    var inner = "{\"keys\":[" + JWK + "]}";
    var outer = "{\"keys\":[],\"embedded\":\"" + inner.replace("\"", "\\\"") + "\"}";

    var message = logger.getRbelConverter().convertElement(response(outer), null);
    var documents = new ArrayList<RbelElement>();
    collectDocumentRoots(message, documents);

    assertThat(documents).hasSize(2);
    for (RbelElement document : documents) {
      assertThat(
              document.findAllInTreeWithKey(
                  "keys", element -> !RbelJsonFacet.isRootOfDocument(element)))
          .hasSize(1);
    }
    assertThat(logger.getRbelKeyManager().findKeyByName("overhead-test-key")).isPresent();
  }

  @Test
  void theKeysAreFoundInTheOrderOfTheRbelPath() {
    var message =
        RbelLogger.build()
            .getRbelConverter()
            .convertElement(
                response("{\"keys\":[1],\"a\":{\"keys\":[2]},\"b\":{\"keys\":[3]}}"), null);
    var body = message.findElement("$.body").orElseThrow();

    assertThat(body.findAllInTreeWithKey("keys"))
        .hasSize(3)
        .containsExactlyElementsOf(body.findRbelPathMembers("$..keys"));
  }

  @Test
  void ofTwoKeysWithTheSameSecretTheOneThatComesFirstInTheRbelPathIsRegistered() {
    var logger = RbelLogger.build();
    var body =
        "{\"a\":{\"keys\":[%s]},\"b\":{\"keys\":[%s]}}"
            .formatted(
                JWK.replace("overhead-test-key", "first"),
                JWK.replace("overhead-test-key", "second"));

    logger.getRbelConverter().convertElement(response(body), null);

    assertThat(logger.getRbelKeyManager().findKeyByName("first")).isPresent();
    assertThat(logger.getRbelKeyManager().findKeyByName("second")).isEmpty();
  }

  @Test
  void aJsonNodeThatIsTheRootOfSomethingElseIsNotTheRootOfADocument() {
    var jsonFacet = RbelJsonFacet.builder().build();
    var node = new RbelElement(new byte[0], null);
    var documentRoot = new RbelElement(new byte[0], null);
    var rootOfOther = new RbelElement(new byte[0], null);
    node.addFacet(jsonFacet);
    documentRoot.addFacet(jsonFacet);
    documentRoot.addFacet(new RbelRootFacet<>(jsonFacet));
    rootOfOther.addFacet(jsonFacet);
    rootOfOther.addFacet(new RbelRootFacet<>(new RbelBinaryFacet()));

    assertThat(RbelJsonFacet.isRootOfDocument(documentRoot)).isTrue();
    assertThat(RbelJsonFacet.isRootOfDocument(node)).isFalse();
    assertThat(RbelJsonFacet.isRootOfDocument(rootOfOther)).isFalse();
    assertThat(RbelJsonFacet.isRootOfDocument(new RbelElement(new byte[0], null))).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"keys\":[" + JWK + "]}",
        "{\"outer\":{\"inner\":{\"keys\":[" + JWK + "]}}}",
      })
  void aKeySetAnywhereInTheMessageIsStillRegistered(String body) {
    var logger = RbelLogger.build();

    logger.getRbelConverter().convertElement(response(body), null);

    assertThat(logger.getRbelKeyManager().findKeyByName("overhead-test-key")).isPresent();
  }
}
