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
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.http.RbelHttpResponseFacet;
import de.gematik.test.tiger.common.config.RbelModificationDescription;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CharsetTest {

  @Test
  void readIsoEncodedMessage_shouldRecognizeEncodingAndGiveCorrectContent() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/isoEncodedMessage.curl", ISO_8859_1);

    final RbelElement convertedMessage =
        RbelLogger.build()
            .getRbelConverter()
            .convertElement(curlMessage.getBytes(ISO_8859_1), null);

    final RbelElement bodyElement = convertedMessage.findElement("$.body").get();
    assertThat(bodyElement.getRawStringContent()).isEqualTo("àáâãäåæçèéêëìíîï\r\n");
    assertThat(bodyElement.getElementCharset()).isEqualTo(ISO_8859_1);
  }

  @Test
  void readJson() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/jsonMessage.curl", ISO_8859_1);

    final RbelElement convertedMessage =
        RbelLogger.build()
            .getRbelConverter()
            .convertElement(curlMessage.getBytes(ISO_8859_1), null);

    final RbelElement bodyElement = convertedMessage.findElement("$.body.string").get();

    assertThat(bodyElement.getElementCharset()).isEqualTo(ISO_8859_1);
    assertThat(bodyElement.getRawStringContent()).isEqualTo("àáâãäåæçèéêëìíîï");
  }

  @Test
  void readXml() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/xmlMessage.curl", ISO_8859_1);

    final RbelElement convertedMessage =
        RbelLogger.build()
            .getRbelConverter()
            .convertElement(curlMessage.getBytes(ISO_8859_1), null);

    final RbelElement bodyElement = convertedMessage.findElement("$..charsetTest.text").get();

    assertThat(bodyElement.getElementCharset()).isEqualTo(ISO_8859_1);
    assertThat(bodyElement.getRawStringContent()).isEqualTo("àáâãäåæçèéêëìíîï");
  }

  @Test
  void modifyJson() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/jsonMessage.curl", ISO_8859_1);

    final RbelLogger rbelLogger = RbelLogger.build();
    final String newContent = "àáâãäåàáâãäåàáâãäå";
    rbelLogger
        .getRbelModifier()
        .addModification(
            RbelModificationDescription.builder()
                .targetElement("$.body.string")
                .replaceWith(newContent)
                .build());
    final RbelElement convertedMessage =
        rbelLogger
            .getRbelModifier()
            .applyModifications(
                rbelLogger
                    .getRbelConverter()
                    .convertElement(curlMessage.getBytes(ISO_8859_1), null));

    final RbelElement bodyElement = convertedMessage.findElement("$.body.string").get();

    assertThat(bodyElement.getElementCharset()).isEqualTo(ISO_8859_1);
    assertThat(bodyElement.getRawStringContent()).isEqualTo(newContent);
  }

  @Test
  void verifyJexlMapHasCharset() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/xmlMessage.curl", ISO_8859_1);

    final RbelElement convertedMessage =
        RbelLogger.build()
            .getRbelConverter()
            .convertElement(curlMessage.getBytes(ISO_8859_1), null);

    final RbelElement bodyElement =
        convertedMessage
            .findElement(
                "$..[?(key=='charsetTest' && charset=='" + ISO_8859_1.displayName() + "')].text")
            .get();

    assertThat(bodyElement.getElementCharset()).isEqualTo(ISO_8859_1);
    assertThat(bodyElement.getRawStringContent()).isEqualTo("àáâãäåæçèéêëìíîï");
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "'',UTF-8",
        "'schmöö:utf8', UTF-8",
        "'us', US-ASCII",
        "'ich mag am liebsten unicode, unicode, unicode', UTF-16",
        "latin1 dance party, ISO-8859-1"
      })
  void parseDefunctCharsetAndExpectCorrectResults(String charsetString, String expectedResult)
      throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
                "src/test/resources/sampleMessages/jsonMessage.curl")
            .replaceFirst("application/json; charset=latin1", charsetString);

    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessage.getBytes(), null);

    assertThat(convertedMessage.hasFacet(RbelHttpResponseFacet.class)).isTrue();
    assertThat(convertedMessage.findElement("$.body").get().getElementCharset().displayName())
        .isEqualTo(expectedResult);
  }
}
