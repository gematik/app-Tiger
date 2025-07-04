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
package de.gematik.rbellogger.modifier;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.jose.RbelJweEncryptionInfo;
import de.gematik.rbellogger.facets.jose.RbelJweFacet;
import de.gematik.rbellogger.modifier.RbelJweWriter.InvalidEncryptionInfo;
import de.gematik.rbellogger.modifier.RbelJweWriter.JweUpdateException;
import de.gematik.rbellogger.modifier.RbelModifier.RbelModificationException;
import de.gematik.test.tiger.common.config.RbelModificationDescription;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class JweModifierTest extends AbstractModifierTest {

  @Test
  void modifyJweHeaderEnc_cantBeModified() throws IOException {
    final RbelElement message =
        readAndConvertCurlMessage("src/test/resources/sampleMessages/jweMessage.curl");

    rbelLogger
        .getRbelModifier()
        .addModification(
            RbelModificationDescription.builder()
                .targetElement("$.body.header.enc")
                .replaceWith("not the real header")
                .build());

    assertThatThrownBy(() -> modifyMessageAndParseResponse(message))
        .isInstanceOf(JweUpdateException.class)
        .hasMessageContaining("Error writing into Jwe")
        .hasRootCauseMessage(
            "not the real header is an unknown, unsupported or unavailable enc algorithm (not one"
                + " of [A128CBC-HS256, A192CBC-HS384, A256CBC-HS512, A128GCM, A192GCM, A256GCM]).");
  }

  @Test
  void modifyJweHeaderAddField_canBeModified() throws IOException {
    final RbelElement message =
        readAndConvertCurlMessage("src/test/resources/sampleMessages/jweMessage.curl");

    rbelLogger
        .getRbelModifier()
        .addModification(
            RbelModificationDescription.builder()
                .targetElement("$.body.header")
                .regexFilter("\"alg\":")
                .replaceWith("\"foo\":1234,\"alg\":")
                .build());

    final RbelElement modifiedMessage = modifyMessageAndParseResponse(message);

    final RbelJweEncryptionInfo encryptionInfo =
        modifiedMessage
            .findElement("$.body.encryptionInfo")
            .get()
            .getFacetOrFail(RbelJweEncryptionInfo.class);

    assertThat(
            modifiedMessage.findElement("$.body.header.foo").map(RbelElement::getRawStringContent))
        .contains("1234");
    assertThat(encryptionInfo.wasDecryptable()).isTrue();
    assertThat(encryptionInfo.getDecryptedUsingKeyWithId().equals("prk_idpEnc"));
  }

  @Test
  void modifyJweHeaderAddField_jweEncrypted() throws IOException {
    final RbelElement message =
        readAndConvertCurlMessage("src/test/resources/sampleMessages/jweMessage.curl");

    rbelLogger
        .getRbelModifier()
        .addModification(
            RbelModificationDescription.builder()
                .targetElement("$.body.header")
                .regexFilter("\"alg\":")
                .replaceWith("\"foo\":1234,\"alg\":")
                .build());

    final RbelElement modifiedMessage = modifyMessageAndParseResponse(message);

    final RbelJweEncryptionInfo encryptionInfo =
        modifiedMessage
            .findElement("$.body.encryptionInfo")
            .get()
            .getFacetOrFail(RbelJweEncryptionInfo.class);

    assertThat(encryptionInfo.wasDecryptable()).isTrue();
    assertThat(encryptionInfo.getDecryptedUsingKeyWithId().equals("prk_idpEnc"));
  }

  @Test
  void modifyJweHeader_cantEditAlg() throws IOException {
    final RbelElement message =
        readAndConvertCurlMessage("src/test/resources/sampleMessages/jweMessage.curl");

    rbelLogger
        .getRbelModifier()
        .addModification(
            RbelModificationDescription.builder()
                .targetElement("$.body.header.alg")
                .replaceWith("ES256")
                .build());

    assertThatThrownBy(() -> modifyMessageAndParseResponse(message))
        .isInstanceOf(JweUpdateException.class)
        .hasMessageContaining("Error writing into Jwe");
  }

  @Test
  void modifyJweBody_shouldContainModifiedContent() throws IOException {
    final RbelElement message =
        readAndConvertCurlMessage("src/test/resources/sampleMessages/jweMessage.curl");

    rbelLogger
        .getRbelModifier()
        .addModification(
            RbelModificationDescription.builder()
                .targetElement("$.body.body.token_key")
                .replaceWith("not the token key")
                .build());

    final RbelElement modifiedMessage = modifyMessageAndParseResponse(message);

    assertThat(modifiedMessage.findElement("$.body").get().hasFacet(RbelJweFacet.class)).isTrue();
    assertThat(
            modifiedMessage
                .findElement("$.body.body.token_key")
                .map(RbelElement::getRawStringContent))
        .contains("not the token key");
  }

  @Test
  void modifyJweBody_jweEncrypted() throws IOException {
    final RbelElement message =
        readAndConvertCurlMessage("src/test/resources/sampleMessages/jweMessage.curl");

    rbelLogger
        .getRbelModifier()
        .addModification(
            RbelModificationDescription.builder()
                .targetElement("$.body.body.token_key")
                .replaceWith("not the token key")
                .build());

    final RbelElement modifiedMessage = modifyMessageAndParseResponse(message);

    final RbelJweEncryptionInfo encryptionInfo =
        modifiedMessage
            .findElement("$.body.encryptionInfo")
            .get()
            .getFacetOrFail(RbelJweEncryptionInfo.class);

    assertThat(encryptionInfo.wasDecryptable()).isTrue();
    assertThat(encryptionInfo.getDecryptedUsingKeyWithId().equals("prk_idpEnc"));
  }

  @Test
  void modifyJweBodyWithString_jweEncrypted() throws IOException {
    final RbelElement message =
        readAndConvertCurlMessage("src/test/resources/sampleMessages/jweMessage.curl");

    rbelLogger
        .getRbelModifier()
        .addModification(
            RbelModificationDescription.builder()
                .targetElement("$.body.body")
                .replaceWith("not the proper body")
                .build());

    final RbelElement modifiedMessage = modifyMessageAndParseResponse(message);

    assertThat(modifiedMessage.findElement("$.body.body").map(RbelElement::getRawStringContent))
        .contains("not the proper body");
    final RbelJweEncryptionInfo encryptionInfo =
        modifiedMessage
            .findElement("$.body.encryptionInfo")
            .get()
            .getFacetOrFail(RbelJweEncryptionInfo.class);

    assertThat(encryptionInfo.wasDecryptable()).isTrue();
    assertThat(encryptionInfo.getDecryptedUsingKeyWithId().equals("prk_idpEnc"));
  }

  @Test
  void modifyJweEncryptionInfo_cantBeRewritten() throws IOException {
    final RbelElement message =
        readAndConvertCurlMessage("src/test/resources/sampleMessages/jweMessage.curl");

    rbelLogger
        .getRbelModifier()
        .addModification(
            RbelModificationDescription.builder()
                .targetElement("$.body.encryptionInfo.decryptedUsingKeyWithId")
                .replaceWith("false key")
                .build());

    assertThatThrownBy(() -> modifyMessageAndParseResponse(message))
        .isInstanceOf(RbelModificationException.class)
        .hasMessageContaining("Could not rewrite element with facets [RbelJweEncryptionInfo]");
  }

  @Test
  void jweCantBeEncrypted() throws IOException, IllegalAccessException {
    final RbelElement message =
        readAndConvertCurlMessage("src/test/resources/sampleMessages/jweMessage.curl");
    var rbelLoggerWithoutKeys = RbelLogger.build();

    rbelLoggerWithoutKeys
        .getRbelModifier()
        .addModification(
            RbelModificationDescription.builder()
                .targetElement("$.body.body.token_key")
                .replaceWith("not the token key")
                .build());

    assertThatThrownBy(() -> rbelLoggerWithoutKeys.getRbelModifier().applyModifications(message))
        .isInstanceOf(InvalidEncryptionInfo.class)
        .hasMessageContaining("public key");
  }

  @Test
  void modifyInvalidJwe_cantBeDecrypted() throws IOException {
    final RbelElement message =
        readAndConvertCurlMessage("src/test/resources/sampleMessages/falseJweMessage.curl");

    rbelLogger
        .getRbelModifier()
        .addModification(
            RbelModificationDescription.builder()
                .targetElement("$.body.body.token_key")
                .replaceWith("not the token key")
                .build());

    final RbelElement modifiedMessage = modifyMessageAndParseResponse(message);

    final RbelJweEncryptionInfo encryptionInfo =
        modifiedMessage
            .findElement("$.body.encryptionInfo")
            .get()
            .getFacetOrFail(RbelJweEncryptionInfo.class);

    assertThat(encryptionInfo.wasDecryptable()).isFalse();
    assertThat(encryptionInfo.getDecryptedUsingKeyWithId().equals("prk_idpEnc"));
  }
}
