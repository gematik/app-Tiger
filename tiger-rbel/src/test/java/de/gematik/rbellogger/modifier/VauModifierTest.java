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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.vau.vau.RbelVauEpaFacet;
import de.gematik.rbellogger.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.util.CryptoUtils;
import de.gematik.rbellogger.util.RbelContent;
import de.gematik.test.tiger.common.config.RbelModificationDescription;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VauModifierTest {

  private RbelLogger rbelLogger;

  @BeforeEach
  public void initRbelLogger() {
    if (rbelLogger == null) {
      rbelLogger =
          RbelLogger.build(
              new RbelConfiguration()
                  .activateConversionFor("epa-vau")
                  .activateConversionFor("erp-vau")
                  .addInitializer(new RbelKeyFolderInitializer("src/test/resources")));
    }
    rbelLogger.getRbelModifier().deleteAllModifications();
  }

  @Test
  void modifyErpVauRequestBody() throws IOException {
    final RbelElement message = readAndConvertRawMessage("src/test/resources/vauErpRequest.b64");

    rbelLogger
        .getRbelModifier()
        .addModification(
            RbelModificationDescription.builder()
                .targetElement("$.body.message.body")
                .replaceWith("<New>Vau inner body</New>")
                .build());

    final RbelElement modifiedMessage = rbelLogger.getRbelModifier().applyModifications(message);

    assertThat(
            modifiedMessage
                .findElement("$.body.message.body.New.text")
                .map(RbelElement::getRawStringContent))
        .contains("Vau inner body");
  }

  @Test
  void modifyErpVauResponseBody() throws IOException {
    rbelLogger
        .getRbelKeyManager()
        .addKey(
            "secretKey",
            new SecretKeySpec(Base64.getDecoder().decode("dGPgkcT15xeXhORNsgc83A=="), "AES"),
            0);
    final RbelElement message = readAndConvertRawMessage("src/test/resources/vauErpResponse.b64");

    rbelLogger
        .getRbelModifier()
        .addModification(
            RbelModificationDescription.builder()
                .targetElement("$.body.message.body")
                .replaceWith("<New>Vau inner body</New>")
                .build());

    final RbelElement modifiedMessage = rbelLogger.getRbelModifier().applyModifications(message);
    assertThat(
            modifiedMessage
                .findElement("$.body.message.body.New.text")
                .map(RbelElement::getRawStringContent))
        .contains("Vau inner body");
  }

  @Test
  void modifyEpaVauRequestBody() {
    rbelLogger =
        RbelLogger.build(
            new RbelConfiguration()
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                .activateConversionFor("epa-vau")
                .addCapturer(
                    RbelFileReaderCapturer.builder()
                        .rbelFile("src/test/resources/vauFlow.tgr")
                        .build()));
    rbelLogger.getRbelCapturer().initialize();
    final RbelElement message = rbelLogger.getMessagesByOrder().get(4);

    rbelLogger
        .getRbelModifier()
        .addModification(
            RbelModificationDescription.builder()
                .targetElement("$.body.message")
                .replaceWith("<New>Vau inner body</New>")
                .build());

    final RbelElement modifiedMessage = rbelLogger.getRbelModifier().applyModifications(message);

    assertThat(
            modifiedMessage
                .findElement("$.body.message.New.text")
                .map(RbelElement::getRawStringContent))
        .get()
        .isEqualTo("Vau inner body");
  }

  @Test
  void writingChunkedContentGivesTheSameVauMessageAsASingleArray() {
    rbelLogger =
        RbelLogger.build(
            new RbelConfiguration()
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                .activateConversionFor("epa-vau")
                .addCapturer(
                    RbelFileReaderCapturer.builder()
                        .rbelFile("src/test/resources/vauFlow.tgr")
                        .build()));
    rbelLogger.getRbelCapturer().initialize();
    final RbelElement message = rbelLogger.getMessagesByOrder().get(4);
    final RbelElement vauElement = message.findElement("$.body").orElseThrow();
    final RbelElement child = message.findElement("$.body.message").orElseThrow();
    final byte[] newBody = "<New>".repeat(40).getBytes(Charset.defaultCharset());
    final RbelContent chunked =
        RbelContent.builder()
            .chunkSize(7)
            .content(
                IntStream.range(0, (newBody.length + 6) / 7)
                    .mapToObj(
                        i ->
                            Arrays.copyOfRange(
                                newBody, i * 7, Math.min(newBody.length, (i + 1) * 7)))
                    .toList())
            .build();
    final RbelContent concatenated =
        RbelContent.concat(
            RbelContent.wrap(Arrays.copyOfRange(newBody, 0, 33)),
            RbelContent.wrap(Arrays.copyOfRange(newBody, 33, newBody.length)));
    final RbelContent slice =
        RbelContent.wrap(ArrayUtils.addAll(new byte[] {1, 2, 3}, newBody))
            .subArray(3, 3 + newBody.length);
    final RbelVauEpaFacet facet = vauElement.getFacetOrFail(RbelVauEpaFacet.class);
    final RbelKey key = facet.getKeyUsed().orElseThrow();
    final RbelContent encrypted = facet.getEncryptedMessage().getContent();
    final byte[] oldCleartext = CryptoUtils.decrypt(encrypted, key.getKey()).orElseThrow();
    final int introLength = 1 + 8 + 4 + ByteBuffer.wrap(oldCleartext, 9, 4).getInt();
    final byte[] expected =
        ArrayUtils.addAll(
            vauElement.getContent().toByteArray(0, RbelVauEpaFacet.KEY_ID_LENGTH),
            RbelVauEpaWriter.encrypt(
                ArrayUtils.addAll(Arrays.copyOf(oldCleartext, introLength), newBody),
                key.getKey().getEncoded(),
                encrypted.toByteArray(0, 12)));
    final RbelVauEpaWriter writer = new RbelVauEpaWriter();

    assertThat(chunked.getChunkSize()).isLessThan(newBody.length);
    for (RbelContent input : List.of(RbelContent.wrap(newBody), chunked, concatenated, slice)) {
      assertThat(writer.write(vauElement, child, input).toByteArray())
          .as("a body of %s", input.getClass().getSimpleName())
          .isEqualTo(expected);
    }
  }

  @Test
  void aVauMessageWithoutAHeaderLengthIsRefusedAtOnceInsteadOfBeingWrittenWithAWrongCleartext() {
    final byte[] key = new byte[16];
    final byte[] cleartext = "VAUClientSigFin".getBytes(UTF_8);
    final byte[] encrypted = RbelVauEpaWriter.encrypt(cleartext, key, new byte[12]);
    final RbelElement vauElement =
        new RbelElement(
            ArrayUtils.addAll(new byte[RbelVauEpaFacet.KEY_ID_LENGTH], encrypted), null);
    vauElement.addFacet(
        RbelVauEpaFacet.builder()
            .encryptedMessage(new RbelElement(encrypted, vauElement))
            .keyUsed(
                Optional.of(
                    RbelKey.builder()
                        .key(new SecretKeySpec(key, "AES"))
                        .keyName("fin")
                        .precedence(RbelKey.PRECEDENCE_KEY_FOLDER)
                        .build()))
            .build());
    final RbelElement child = new RbelElement(new byte[0], vauElement);

    assertThatThrownBy(
            () ->
                new RbelVauEpaWriter()
                    .write(vauElement, child, RbelContent.wrap("<New/>".getBytes(UTF_8))))
        .isInstanceOf(RbelModifier.RbelModificationException.class)
        .hasMessageContaining("header");
  }

  private RbelElement readAndConvertRawMessage(String fileName) throws IOException {
    String rawMessage = FileUtils.readFileToString(new File(fileName), Charset.defaultCharset());
    return rbelLogger
        .getRbelConverter()
        .convertElement(Base64.getDecoder().decode(rawMessage), null);
  }
}
