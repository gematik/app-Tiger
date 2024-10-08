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

package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelAsn1Facet;
import de.gematik.rbellogger.data.facet.RbelAsn1TaggedValueFacet;
import de.gematik.rbellogger.data.facet.RbelUriFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.IOException;
import java.math.BigInteger;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class RbelAsn1ConverterTest {

  private static RbelLogger rbelLogger;

  @BeforeAll
  public static void initRbelLogger() {
    rbelLogger =
        RbelLogger.build(
            new RbelConfiguration()
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                .activateConversionFor("asn1"));
  }

  public void parseRezepsCapture() {
    rbelLogger =
        RbelLogger.build(
            new RbelConfiguration()
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                .addCapturer(
                    RbelFileReaderCapturer.builder()
                        .rbelFile("src/test/resources/rezepsFiltered.tgr")
                        .build())
                .activateConversionFor("asn1"));
    rbelLogger.getRbelCapturer().initialize();
  }

  @SneakyThrows
  @Test
  void shouldRenderCleanHtml() {
    parseRezepsCapture();
    assertThat(RbelHtmlRenderer.render(rbelLogger.getMessageHistory())).isNotBlank();
  }

  @Test
  void checkXmlInPkcs7InXml() throws IOException {
    // check OID
    final RbelElement convertMessage =
        rbelLogger
            .getRbelConverter()
            .convertElement(
                readCurlFromFileWithCorrectedLineBreaks(
                    "src/test/resources/xmlWithNestedPkcs7.curl"),
                null);
    assertThat(
            convertMessage.findRbelPathMembers("$..author.type.value").stream()
                .map(RbelElement::getRawStringContent)
                .collect(Collectors.toList()))
        .contains("Practitioner");
  }

  @Test
  void asn1EnumeratedShouldBeParsed() {
    final RbelElement convertMessage =
        rbelLogger.getRbelConverter().convertElement("MAMKAVU=", null);

    assertThat(convertMessage.findRbelPathMembers("$.0").get(0).seekValue(BigInteger.class))
        .get()
        .extracting(BigInteger::intValueExact)
        .isEqualTo(85);
  }

  @Test
  void testVariousRbelPathInPcap() {
    parseRezepsCapture();
    // check OID
    final RbelElement rbelMessage = rbelLogger.getMessageList().get(58);
    // The extractChildPath moves the pointer from the given element to its child so subsequent
    // chaining
    // starts from the "new root" element and thus the code will fail,
    assertThat(rbelMessage) // NOSONAR
        .extractChildWithPath("$.body.0.2.0")
        .hasValueEqualTo("1.2.840.10045.4.3.2");
    // check X509-Version (Tagged-sequence)
    assertThat(rbelMessage)
        .extractChildWithPath("$.body.0.0.content")
        .hasValueEqualTo(BigInteger.valueOf(2));
    // check OCSP URL
    assertThat(rbelMessage)
        .extractChildWithPath("$.body.0.7.content.3.1.content.0.1.content.content")
        .hasFacet(RbelUriFacet.class);
    assertThat(rbelMessage)
        .extractChildWithPath("$.body.0.7.content.3.1.content.0")
        .hasFacet(RbelAsn1Facet.class);
    assertThat(rbelMessage)
        .extractChildWithPath("$.body.0.7.content.3.1.content.0")
        .hasFacet(RbelAsn1Facet.class);
    assertThat(rbelMessage)
        .extractChildWithPath("$.body.0.7.content.3.1.content.0.1")
        .hasFacet(RbelAsn1TaggedValueFacet.class);
    assertThat(rbelMessage)
        .extractChildWithPath("$.body.0.7.content.3.1.content.0.1.tag")
        .hasValueEqualTo(6);
    assertThat(rbelMessage)
        .extractChildWithPath("$.body.0.7.content.3.1.content.0.1.content.content")
        .hasStringContentEqualTo("http://ehca.gematik.de/ocsp/");
    // Parse y-coordinate of signature (Nested in BitString)
    assertThat(rbelMessage)
        .extractChildWithPath("$.body.2.content.1")
        .hasValueEqualTo(
            new BigInteger(
                "9528585714247878020400211740123936754253798904841060501006300662224159406199"));
  }
}
