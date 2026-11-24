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
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.rbellogger.facets.http.RbelHttpResponseFacet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RbelHttpResponseConverterTest {

  private static RbelElement convert(String message) {
    return RbelLogger.build().getRbelConverter().convertElement(message.getBytes(UTF_8), null);
  }

  private static String text(RbelElement message, String path) {
    return message.findElement(path).orElseThrow().getRawStringContent();
  }

  @ParameterizedTest
  @ValueSource(strings = {"\r\n", "\n"})
  void theReasonPhraseIsTheRestOfTheStatusLineWhateverTheLineBreak(String eol) {
    var message =
        convert(
            "HTTP/1.1 404 Not Found Here" + eol + "Content-Length: 9" + eol + eol + "Body text");

    assertThat(text(message, "$.responseCode")).isEqualTo("404");
    assertThat(text(message, "$.reasonPhrase")).isEqualTo("Not Found Here");
    assertThat(text(message, "$.body")).isEqualTo("Body text");
  }

  @Test
  void aBodyInSeveralChunksIsDecodedCompletely() {
    var message =
        convert(
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"
                + "5\r\nHello\r\n6\r\n World\r\n0\r\n\r\n");

    assertThat(text(message, "$.body")).isEqualTo("Hello World");
    assertThat(message.getSize()).isEqualTo(message.getContent().size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\r\n", "\n"})
  void aResponseWithoutAReasonPhraseHasNone(String eol) {
    var message = convert("HTTP/1.1 200" + eol + eol + "Body text");

    assertThat(text(message, "$.responseCode")).isEqualTo("200");
    assertThat(text(message, "$.reasonPhrase")).isNullOrEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"\r\n", "\n"})
  void aStatusLineWithoutACodeIsNoResponseAndRaisesNoException(String eol) {
    var message = convert("HTTP/1.1" + eol + "A:b" + eol + eol + "Body text");

    assertThat(message.hasFacet(RbelHttpResponseFacet.class)).isFalse();
    assertThat(message.getFacets())
        .filteredOn(RbelNoteFacet.class::isInstance)
        .map(RbelNoteFacet.class::cast)
        .map(RbelNoteFacet::getValue)
        .noneMatch(note -> note.contains("Exception during conversion"));
  }
}
