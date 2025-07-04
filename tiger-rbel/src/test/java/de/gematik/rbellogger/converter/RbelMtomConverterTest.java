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

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RbelMtomConverterTest {

  private static RbelLogger rbelLogger;

  @BeforeAll
  @SneakyThrows
  public static void init() {
    try (final RbelFileReaderCapturer rbelFileReaderCapturer = getRbelFileReaderCapturer()) {

      rbelLogger =
          RbelLogger.build(
              new RbelConfiguration()
                  .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                  .activateConversionFor("epa-vau")
                  .addCapturer(rbelFileReaderCapturer));
      rbelFileReaderCapturer.initialize();
    }
  }

  private static RbelFileReaderCapturer getRbelFileReaderCapturer() {
    return RbelFileReaderCapturer.builder()
        .rbelFile("src/test/resources/vauEp2FlowUnixLineEnding.tgr")
        .build();
  }

  @Test
  void shouldRenderCleanHtml() {
    assertThat(RbelHtmlRenderer.render(rbelLogger.getMessageHistory())).isNotBlank();
  }

  @Test
  @DisplayName("MTOM XML - should be parsed correctly")
  void mtomXml_shouldBeParsedCorrectly() {
    assertThat(rbelLogger.getMessageList().get(34))
        .extractChildWithPath("$..Envelope.['xmlns:soap']")
        .hasStringContentEqualTo("http://www.w3.org/2003/05/soap-envelope");

    assertThat(
            rbelLogger
                .getMessageList()
                .get(34)
                .findRbelPathMembers("$..EncryptionMethod.Algorithm")
                .stream()
                .map(RbelElement::getRawStringContent)
                .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(
            "http://www.w3.org/2009/xmlenc11#aes256-gcm",
            "http://www.w3.org/2009/xmlenc11#aes256-gcm");
  }

  @Test
  @DisplayName("MTOM XML with data - should be parsed correctly")
  void mtomXmlWithData_shouldBeParsedCorrectly() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks("src/test/resources/sampleMessages/dataMtom.curl");

    final RbelElement convertedMessage =
        rbelLogger.getRbelConverter().convertElement(curlMessage.getBytes(), null);

    assertThat(convertedMessage) // NOSONAR
        .extractChildWithPath("$..Envelope..MandantId.text")
        .hasStringContentEqualTo("m_raf");
    assertThat(convertedMessage)
        .extractChildWithPath("$.body.dataParts.0.content")
        .asString()
        .startsWith("%PDF-1.6");
    assertThat(convertedMessage)
        .extractChildWithPath("$.body.dataParts.0.xpath")
        .hasValueEqualTo(
            "/SOAP-ENV:Envelope/SOAP-ENV:Body/ns4:SignDocument/ns4:SignRequest/ns4:Document/ns6:Base64Data/xop:Include");

    FileUtils.writeStringToFile(
        new File("target/mtom.html"),
        RbelHtmlRenderer.render(
            List.of(
                convertedMessage,
                rbelLogger
                    .getRbelConverter()
                    .convertElement(
                        readCurlFromFileWithCorrectedLineBreaks(
                                "src/test/resources/sampleMessages/jsonMessage.curl")
                            .getBytes(),
                        null))));
  }
}
