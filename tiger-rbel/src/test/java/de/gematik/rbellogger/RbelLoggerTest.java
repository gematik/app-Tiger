/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.rbellogger;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.rbellogger.facets.http.RbelHttpMessageFacet;
import de.gematik.rbellogger.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import javax.crypto.spec.SecretKeySpec;
import lombok.SneakyThrows;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

class RbelLoggerTest {

  @Test
  void addNoteToHeader() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/jwtMessage.curl");

    final RbelLogger rbelLogger = RbelLogger.build();
    rbelLogger.getValueShader().addJexlNoteCriterion("key == 'Version'", "Extra note");
    final RbelHttpMessageFacet convertedMessage =
        rbelLogger
            .getRbelConverter()
            .parseMessage(curlMessage.getBytes(), new RbelMessageMetadata())
            .getFacetOrFail(RbelHttpMessageFacet.class);

    assertThat(convertedMessage.getHeader().getFirst("Version").get().getNotes())
        .extracting(RbelNoteFacet::getValue)
        .containsExactly("Extra note");
  }

  @Test
  void addNoteToHttpHeaderButNotBody() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/jwtMessage.curl");

    final RbelLogger rbelLogger = RbelLogger.build();
    rbelLogger.getValueShader().addJexlNoteCriterion("path == 'header'", "Header note");
    final RbelHttpMessageFacet convertedMessage =
        rbelLogger
            .getRbelConverter()
            .parseMessage(curlMessage.getBytes(), new RbelMessageMetadata())
            .getFacetOrFail(RbelHttpMessageFacet.class);

    assertThat(convertedMessage.getHeader().getNotes())
        .extracting(RbelNoteFacet::getValue)
        .containsExactly("Header note");
    assertThat(convertedMessage.getBody().getNotes()).isEmpty();
  }

  @SneakyThrows
  @Test
  void multipleKeysWithSameId_shouldSelectCorrectOne() {
    final RbelFileReaderCapturer fileReaderCapturer =
        RbelFileReaderCapturer.builder()
            .rbelFile("src/test/resources/deregisterPairing.tgr")
            .build();
    final RbelLogger rbelLogger =
        RbelLogger.build(
            new RbelConfiguration()
                .addKey(
                    "IDP symmetricEncryptionKey",
                    new SecretKeySpec(DigestUtils.sha256("falscherTokenKey"), "AES"),
                    RbelKey.PRECEDENCE_KEY_FOLDER)
                .addKey(
                    "IDP symmetricEncryptionKey",
                    new SecretKeySpec(
                        DigestUtils.sha256("geheimerSchluesselDerNochGehashtWird"), "AES"),
                    RbelKey.PRECEDENCE_KEY_FOLDER)
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                .addCapturer(fileReaderCapturer));

    fileReaderCapturer.initialize();
    fileReaderCapturer.close();

    FileUtils.writeStringToFile(
        new File("target/pairingList.html"),
        new RbelHtmlRenderer().doRender(rbelLogger.getMessageList()),
        Charset.defaultCharset());

    assertThat(rbelLogger.getMessageList().get(9))
        .extractChildWithPath("$.header.Location.code.value.encryptionInfo.decryptedUsingKeyWithId")
        .hasValueEqualTo("IDP symmetricEncryptionKey");
  }
}
