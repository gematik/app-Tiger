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
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.testutil.RbelElementAssertion;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

class RbelAuthorizationHeaderConverterTest {

  @Test
  void shouldFindJwtInBearerHeaderAttributer() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/bearerToken.curl");

    final RbelLogger logger = RbelLogger.build();
    final RbelElement convertedMessage =
        logger
            .getRbelConverter()
            .parseMessage(
                curlMessage.getBytes(StandardCharsets.UTF_8),
                new RbelMessageMetadata().withTransmissionTime(ZonedDateTime.now()));

    assertThat(convertedMessage.findRbelPathMembers("$.header.Authorization.BearerToken"))
        .isNotEmpty();
  }

  @Test
  void shouldRenderBearerToken() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/bearerToken.curl");

    final RbelLogger logger = RbelLogger.build();
    logger
        .getRbelConverter()
        .parseMessage(
            curlMessage.getBytes(StandardCharsets.UTF_8),
            new RbelMessageMetadata().withTransmissionTime(ZonedDateTime.now()));

    final String renderingOutput = RbelHtmlRenderer.render(logger.getMessageHistory());
    assertThat(renderingOutput)
        .contains("Carvalho")
        .contains("https://idp.zentral.idp.splitdns.ti-dienste.de");
    FileUtils.writeStringToFile(new File("target/bearerToken.html"), renderingOutput);
  }

  @Test
  void shouldParseDpopAuthorizationHeader() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
                "src/test/resources/sampleMessages/bearerToken.curl")
            .replace("Authorization: Bearer ", "Authorization: DPoP ");

    final RbelLogger logger = RbelLogger.build();
    final RbelElement convertedMessage =
        logger
            .getRbelConverter()
            .parseMessage(
                curlMessage.getBytes(StandardCharsets.UTF_8),
                new RbelMessageMetadata().withTransmissionTime(ZonedDateTime.now()));

    assertThat(convertedMessage.findRbelPathMembers("$.header.Authorization.DpopToken"))
        .isNotEmpty();
  }

  @Test
  void shouldParseBasicAuthorizationHeader() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
                "src/test/resources/sampleMessages/bearerToken.curl")
            .replaceFirst("Bearer .*", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==");

    final RbelLogger logger = RbelLogger.build();
    final RbelElement convertedMessage =
        logger
            .getRbelConverter()
            .parseMessage(
                curlMessage.getBytes(StandardCharsets.UTF_8),
                new RbelMessageMetadata().withTransmissionTime(ZonedDateTime.now()));

    RbelElementAssertion.assertThat(convertedMessage)
        .hasStringContentEqualToAtPosition("$.header.Authorization.username", "Aladdin")
        .hasStringContentEqualToAtPosition("$.header.Authorization.password", "open sesame");
  }
}
