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

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelBinaryFacet;
import de.gematik.rbellogger.facets.jackson.RbelCborFacet;
import de.gematik.rbellogger.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.assertj.core.api.Assertions;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RbelCborConverterTest {

  private static RbelLogger rbelLogger;
  private final ObjectMapper jsonMapper =
      new ObjectMapper().configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
  private final CBORMapper cborMapper = new CBORMapper();

  @BeforeAll
  static void initRbelLogger() {
    rbelLogger =
        RbelLogger.build(
            new RbelConfiguration()
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources")));
  }

  @Test
  void parseBasicCborMessage() {
    final RbelElement convertMessage =
        rbelLogger
            .getRbelConverter()
            .convertElement(HexFormat.of().parseHex("bf6346756ef563416d7421ff"), null);

    assertThat(convertMessage)
        .extractChildWithPath("$.Amt")
        .hasValueEqualTo(-2L)
        .andTheInitialElement()
        .extractChildWithPath("$.Fun")
        .hasValueEqualTo(true);
  }

  @Test
  @SneakyThrows
  void parseBasicCborMessageWithBinary() {
    final RbelElement convertMessage =
        rbelLogger
            .getRbelConverter()
            .convertElement(
                HexFormat.of()
                    .parseHex("a266626173653634684151494442413d3d6662696e6172794401020304"),
                null);

    var renderedHtml = RbelHtmlRenderer.render(List.of(convertMessage));

    Assertions.assertThat(renderedHtml).contains("base64 encoded binary content");

    assertThat(convertMessage)
        .extractChildWithPath("$.base64")
        .hasStringContentEqualTo("AQIDBA==")
        .doesNotHaveFacet(RbelBinaryFacet.class)
        .andTheInitialElement()
        .extractChildWithPath("$.binary")
        .hasFacet(RbelBinaryFacet.class)
        .hasStringContentEqualTo(new String(new byte[] {1, 2, 3, 4}));
  }

  @Test
  void arrayCborMessage() {
    final RbelElement convertMessage =
        rbelLogger
            .getRbelConverter()
            .convertElement(
                Base64.getDecoder().decode("mBkBAgMEBQYHCAkKCwwNDg8QERITFBUWFxgYGBk="), null);

    assertThat(convertMessage)
        .extractChildWithPath("$.24")
        .hasValueEqualTo(25L)
        .andTheInitialElement()
        .extractChildWithPath("$.0")
        .hasValueEqualTo(1L);
  }

  @Test
  void structuredMessage() {
    final RbelElement convertMessage =
        rbelLogger
            .getRbelConverter()
            .convertElement(Base64.getDecoder().decode("nwGCAgOfBAX//w=="), null);

    assertThat(convertMessage)
        .extractChildWithPath("$.0")
        .hasValueEqualTo(1L)
        .andTheInitialElement()
        .extractChildWithPath("$.1.1")
        .hasValueEqualTo(3L)
        .andTheInitialElement()
        .extractChildWithPath("$.2.1")
        .hasValueEqualTo(5L);
  }

  @Test
  void longerMessage() {
    final RbelElement convertMessage =
        rbelLogger
            .getRbelConverter()
            .convertElement(
                Hex.decode(
                    "d9010083a34472616e6b0445636f756e741901a1446e616d6548436f636b7461696ca3d819024442617468d81901190138d8190004a3d8190244466f6f64d819011902b3d8190004"),
                null);

    assertThat(convertMessage)
        .extractChildWithPath("$.0.name")
        .hasStringContentEqualTo("Cocktail")
        .andTheInitialElement()
        .extractChildWithPath("$.1.count")
        .hasValueEqualTo(312L)
        .andTheInitialElement()
        .extractChildWithPath("$.2.rank")
        .hasValueEqualTo(4L);
  }

  @SneakyThrows
  @Test
  void httpCborMessage() {
    final byte[] cborContent =
        Hex.decode(
            "D90100D81C86D81C86656669727374645365616E646C61737465486F6164656A6F636375706174696F6E66777269746572D81D01D81D01D81C86D81900D81901D8190266436F6E6E6572D819046A70726F6772616D6D6572D81D02D81D02");
    final RbelElement convertMessage =
        rbelLogger
            .getRbelConverter()
            .convertElement(
                ArrayUtils.addAll(
                    ("HTTP/1.1 200\r\nConnection: keep-alive\r\ncontent-length:"
                            + cborContent.length
                            + "\r\n\r\n")
                        .getBytes(),
                    cborContent),
                null);

    assertThatNoException()
        .isThrownBy(() -> RbelHtmlRenderer.render(rbelLogger.getMessageHistory()));

    assertThat(convertMessage)
        .extractChildWithPath("$.body.0.1")
        .hasStringContentEqualTo("Sean")
        .andTheInitialElement()
        .extractChildWithPath("$.body.3.5")
        .hasStringContentEqualTo("programmer");
  }

  @SneakyThrows
  @ParameterizedTest
  @CsvSource({
    "$.recipient.1.identifier.value, FooBar",
    "$.meta.tag.1.display, Something else",
    "$.topping.[?(@.id == '5001')].type, None"
  })
  void convertJsonToCborAndThenParseIt_givenRbelPathShouldYieldGivenResult(
      String rbelPath, String result) {
    final byte[] cborBytes =
        cborMapper.writeValueAsBytes(
            jsonMapper.readTree(
                FileUtils.readFileToByteArray(new File("src/test/resources/jexlWorkshop.json"))));

    final RbelElement rbelElement = rbelLogger.getRbelConverter().convertElement(cborBytes, null);

    assertThat(rbelElement).extractChildWithPath(rbelPath).hasStringContentEqualTo(result);
  }

  @Test
  void avoidEmptyCborFragments() {
    final RbelElement convertMessage =
        rbelLogger.getRbelConverter().convertElement(Base64.getDecoder().decode("oA=="), null);

    assertThat(convertMessage).andPrintTree().doesNotHaveFacet(RbelCborFacet.class);
  }
}
