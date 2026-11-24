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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.facets.jackson.RbelJsonFacet;
import de.gematik.rbellogger.facets.jose.RbelJwtFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.util.RbelContent;
import de.gematik.rbellogger.util.RbelSocketAddress;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("de.gematik.test.tiger.common.SmokeTest")
class JsonConverterTest {

  @ParameterizedTest
  @ValueSource(ints = {100, 8192, 8193, 100_000})
  void jsonOfAnySizeIsReadWithTheCharsetOfTheElement(int size) {
    String value = "\u00e4" + "x".repeat(size - 9);
    byte[] bytes = ("{\"k\":\"" + value + "\"}").getBytes(StandardCharsets.ISO_8859_1);
    var element =
        new RbelElement(
            null, RbelContent.of(bytes), null, Optional.of(StandardCharsets.ISO_8859_1));

    RbelLogger.build().getRbelConverter().convertElement(element);

    assertThat(bytes).hasSize(size);
    assertThat(element).hasStringContentEqualToAtPosition("$.k", value);
  }

  @ParameterizedTest
  @CsvSource({"8192,false", "8193,true"})
  void onlyJsonLargerThanTheReaderBufferIsReadThroughAStream(int size, boolean throughStream) {
    byte[] bytes = ("{\"k\":\"" + "x".repeat(size - 8) + "\"}").getBytes(StandardCharsets.UTF_8);
    RbelContent content = spy(RbelContent.of(bytes));
    var element = RbelElement.create(content, null);

    RbelLogger.build().getRbelConverter().convertElement(element);

    assertThat(element.hasFacet(RbelJsonFacet.class)).isTrue();
    if (throughStream) {
      verify(content, atLeastOnce()).toInputStream();
    } else {
      verify(content, never()).toInputStream();
    }
  }

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
  void convertTrivialJsons_shouldAddFacet() {
    final String myMessage = "<html><head>[]</head><body>{}</body></html>";

    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(myMessage, null);

    assertThat(convertedMessage)
        .extractChildWithPath("$.html.head.text")
        .hasFacet(RbelJsonFacet.class)
        .andTheInitialElement()
        .extractChildWithPath("$.html.body.text")
        .hasFacet(RbelJsonFacet.class);
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
            .receiver(
                RbelElement.wrap(null, convertedMessage, RbelSocketAddress.create("recipient", 1)))
            .sender(RbelElement.wrap(null, convertedMessage, RbelSocketAddress.create("sender", 1)))
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
            convertedMessage
                .getFirst("body")
                .get()
                .traverseNestedMembers()
                .filter(el -> el.hasFacet(RbelJwtFacet.class))
                .findAny())
        .isPresent();
  }

  @Test
  void testEmptyJsonBody() {
    String json = "{}";
    RbelElement element = RbelLogger.build().getRbelConverter().convertElement(json, null);

    assertThat(element).hasFacet(RbelJsonFacet.class);
  }

  @Test
  void testEmptyJsonArray() {
    String json = "[]";
    RbelElement element = RbelLogger.build().getRbelConverter().convertElement(json, null);

    assertThat(element).hasFacet(RbelJsonFacet.class);
  }
}
