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
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.facets.jackson.RbelJsonFacet;
import de.gematik.rbellogger.facets.jose.RbelJwtFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonConverterTest {

  @Test
  void convertMessage_shouldGiveJsonBody() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/jsonMessage.curl");

    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);

    assertThat(convertedMessage).extractChildWithPath("$.body").hasFacet(RbelJsonFacet.class);
  }

  @Test
  void convertTrivialJsons_shouldNotAddFacet() {
    final String myMessage = "<html><head>[]</head><body>{}</body></html>";

    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(myMessage, null);

    assertThat(convertedMessage)
        .extractChildWithPath("$.html.head.text")
        .doesNotHaveFacet(RbelJsonFacet.class)
        .andTheInitialElement()
        .extractChildWithPath("$.html.body.text")
        .doesNotHaveFacet(RbelJsonFacet.class);
  }

  @Test
  void shouldRenderCleanHtml() throws IOException {
    final RbelElement convertedMessage =
        RbelLogger.build()
            .getRbelConverter()
            .convertElement(
                readCurlFromFileWithCorrectedLineBreaks(
                    "src/test/resources/sampleMessages/idpEncMessage.curl"),
                null);
    convertedMessage.addFacet(
        RbelTcpIpMessageFacet.builder()
            .receiver(RbelElement.wrap(null, convertedMessage, new RbelHostname("recipient", 1)))
            .sender(RbelElement.wrap(null, convertedMessage, new RbelHostname("sender", 1)))
            .build());
    assertThat(RbelHtmlRenderer.render(List.of(convertedMessage))).isNotBlank();
  }

  @Test
  void jsonMessageWithNestedJwt_shouldFindAndPresentNestedItems() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/getChallenge.curl");

    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessage.getBytes(), null);

    assertThat(RbelHtmlRenderer.render(List.of(convertedMessage))).isNotBlank();

    assertThat(
            convertedMessage.getFirst("body").get().traverseAndReturnNestedMembers().stream()
                .filter(el -> el.hasFacet(RbelJwtFacet.class))
                .findAny())
        .isPresent();
  }
}
