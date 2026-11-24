/*
 *
 * Copyright 2021-2026 gematik GmbH
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

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.core.RbelValueFacet;
import de.gematik.rbellogger.facets.jackson.RbelCborFacet;
import de.gematik.rbellogger.facets.pki.base64.RbelBase64Facet;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RbelBase64JsonConverterTest {

  private static RbelElement parseJsonBody(String body) {
    var message =
        "POST /x HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nContent-Length: "
            + body.length()
            + "\r\n\r\n"
            + body;
    return RbelLogger.build()
        .getRbelConverter()
        .parseMessage(message.getBytes(StandardCharsets.UTF_8), new RbelMessageMetadata());
  }

  private static RbelElement find(RbelElement message, String path) {
    return message.findRbelPathMembers(path).get(0);
  }

  @Test
  void base64EncodedJson_isDecoded_withBothAlphabets() {
    var message = parseJsonBody("{\"standard\":\"eyJhIjoxfQ==\",\"url\":\"eyJiIjoyfQ\"}");

    assertThat(find(message, "$.body.standard").hasFacet(RbelBase64Facet.class)).isTrue();
    assertThat(find(message, "$.body.url").hasFacet(RbelBase64Facet.class)).isTrue();
    assertThat(find(message, "$.body.standard").getChildNodes()).hasSize(1);
  }

  @Test
  void textThatNoEncodingCanProduce_isLeftAlone() {
    var message =
        parseJsonBody("{\"space\":\"hello world\",\"length\":\"abcde\",\"sign\":\"a*b=\"}");

    assertThat(find(message, "$.body.space").hasFacet(RbelBase64Facet.class)).isFalse();
    assertThat(find(message, "$.body.length").hasFacet(RbelBase64Facet.class)).isFalse();
    assertThat(find(message, "$.body.sign").hasFacet(RbelBase64Facet.class)).isFalse();
  }

  @Test
  void moreThanTwoPaddingCharacters_areNotBase64_whateverTheirNumber() {
    var message =
        parseJsonBody(
            "{\"many\":\""
                + "=".repeat(300_000)
                + "\",\"three\":\"abcd===\",\"two\":\"eyJhIjoxfQ==\"}");

    assertThat(find(message, "$.body.many").hasFacet(RbelBase64Facet.class)).isFalse();
    assertThat(find(message, "$.body.three").hasFacet(RbelBase64Facet.class)).isFalse();
    assertThat(find(message, "$.body.two").hasFacet(RbelBase64Facet.class)).isTrue();
  }

  @Test
  void bytesBothAlphabetsDecodeTo_areOfferedToTheConvertersOnce() {
    var converter = RbelLogger.build().getRbelConverter();
    var offered = new AtomicInteger();
    converter.addConverter(
        new RbelConverterPlugin() {
          @Override
          public void consumeElement(RbelElement element, RbelConversionExecutor executor) {
            if (Arrays.equals(element.getRawContent(), "ABCDEFGH".getBytes())) {
              offered.incrementAndGet();
            }
          }
        });

    converter.convertElement("QUJDREVGR0g=".getBytes(), null);

    assertThat(offered).hasValue(1);
  }

  @Test
  void numbersAndBooleans_areValuesWithoutFurtherConversion() {
    var message = parseJsonBody("{\"int\":1234,\"float\":1.5,\"flag\":true}");

    for (String name : new String[] {"int", "float", "flag"}) {
      var element = find(message, "$.body." + name);
      assertThat(element.hasFacet(RbelBase64Facet.class)).as(name).isFalse();
      assertThat(element.hasFacet(RbelCborFacet.class)).as(name).isFalse();
      assertThat(element.hasFacet(RbelValueFacet.class)).as(name).isTrue();
    }
    assertThat(find(message, "$.body.int").getRawStringContent()).isEqualTo("1234");
  }
}
