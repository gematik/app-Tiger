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
import de.gematik.rbellogger.facets.asn1.RbelAsn1Facet;
import de.gematik.rbellogger.facets.asn1.RbelAsn1TaggedValueFacet;
import de.gematik.rbellogger.facets.uri.RbelUriFacet;
import de.gematik.rbellogger.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@Slf4j
public class RbelAsn1ConverterTest {

  private static RbelLogger rbelLogger;

  @SneakyThrows
  @BeforeAll
  public static void initRbelLogger() {
    rbelLogger =
        RbelLogger.build(
            new RbelConfiguration()
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                .addCapturer(
                    RbelFileReaderCapturer.builder()
                        .rbelFile("src/test/resources/rezepsFiltered.tgr")
                        .build())
                .activateConversionFor("asn1")
                .activateConversionFor("erp-vau"));
    rbelLogger.getRbelCapturer().initialize();
  }

  @SneakyThrows
  @Test
  void shouldRenderCleanHtml() {
    assertThat(RbelHtmlRenderer.render(rbelLogger.getMessageHistory())).isNotBlank();
  }

  @Test
  void checkXmlInPkcs7InXml() throws IOException {
    // check OID
    /*RbelElement convertMessage = null;
    long t1 = System.currentTimeMillis();
    final String input =
        readCurlFromFileWithCorrectedLineBreaks("src/test/resources/xmlWithNestedPkcs7.curl");
    for (int i = 0; i < 1; i++) {
      convertMessage = rbelLogger.getRbelConverter().convertElement(input, null);
    }
    long t2 = System.currentTimeMillis();
    for (int i = 0; i < 100; i++) {
      RbelHtmlRenderer.render(rbelLogger.getMessageList());
    }
    long t3 = System.currentTimeMillis();
    System.out.println("Parsing Time: " + (t2 - t1) / 1 + "ms");
    System.out.println("Rendering Time: " + (t3 - t2) / 100 + "ms");*/
    val convertMessage =
        rbelLogger
            .getRbelConverter()
            .convertElement(
                readCurlFromFileWithCorrectedLineBreaks(
                    "src/test/resources/xmlWithNestedPkcs7.curl"),
                null);
    assertThat(
            convertMessage.findRbelPathMembers("$..author.type.value").stream()
                .map(RbelElement::getRawStringContent)
                .toList())
        .contains("Practitioner");
  }

  @Test
  void charsetShouldBeDecodedCorrectly() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/certificate.curl");

    final RbelElement convertedMessage =
        rbelLogger.getRbelConverter().convertElement(curlMessage.getBytes(), null);

    assertThat(convertedMessage.findElement("$.body.0.5.0.0.1.content").get().getElementCharset())
        .isEqualTo(StandardCharsets.US_ASCII);
    assertThat(convertedMessage.findElement("$.body.0.5.1.0.1.content").get().getElementCharset())
        .isEqualTo(StandardCharsets.UTF_8);
  }

  @Test
  void parseEmbededAsn1() throws IOException {
    final RbelElement convertMessage =
        rbelLogger
            .getRbelConverter()
            .convertElement(
                readCurlFromFileWithCorrectedLineBreaks("src/test/resources/smallNestedAsn1.curl"),
                null);

    // just some assertions into the nested ASN.1 structure
    assertThat(convertMessage)
        .extractChildWithPath("$..[?(@.0.name == 'pkcs1-MGF')]")
        .hasGivenValueAtPosition("$.1.0.name", "sha-256");
  }

  @Test
  void parseXmlWithRandomLinebreaks_shouldTrimStringBeforeParsing() {
    final RbelElement convertMessage =
        rbelLogger
            .getRbelConverter()
            .convertElement(
                "<data>\r\r"
                    + "MEEGCSqGSIb3DQEBCjA0oA8wDQYJYIZIAWUDBAIBBQChHDAaBgkqhkiG9w0BAQgwDQYJYIZIAWUDBAIBBQCiAwIBIA"
                    + "\t \n"
                    + " </data>",
                null);

    // just some assertions into the nested ASN.1 structure
    assertThat(convertMessage)
        .extractChildWithPath("$..[?(@.0.name == 'pkcs1-MGF')]")
        .hasGivenValueAtPosition("$.1.0.name", "sha-256");
  }

  @Test
  void asn1EnumeratedShouldBeParsed() {
    final RbelElement convertMessage =
        rbelLogger.getRbelConverter().convertElement("MAMKAVU=", null);

    assertThat(convertMessage).hasGivenValueAtPosition("$.0", BigInteger.valueOf(85L));
  }

  @Test
  void nestedStringShouldBePresentInContentNodeAndValue() {
    final RbelElement convertMessage =
        rbelLogger.getRbelConverter().convertElement("MAkGA1UEBhMCREU=", null);

    assertThat(convertMessage)
        .hasGivenValueAtPosition("$.1", "DE")
        .extractChildWithPath("$.1.content")
        .hasStringContentEqualTo("DE")
        .hasCharset(StandardCharsets.US_ASCII);
  }

  @Test
  void testVariousRbelPathInPcap() {
    // check OID
    final RbelElement rbelMessage = rbelLogger.getMessageList().get(58);
    // The extractChildPath moves the pointer from the given element to its child so subsequent
    // chaining
    // starts from the "new root" element and thus the code will fail,
    assertThat(rbelMessage) // NOSONAR
        .hasGivenValueAtPosition("$.body.0.2.0", "1.2.840.10045.4.3.2")
        // check X509-Version (Tagged-sequence)
        .hasGivenValueAtPosition("$.body.0.0.content", BigInteger.valueOf(2))
        // check OCSP URL
        .hasGivenFacetAtPosition(
            "$.body.0.7.content.3.1.content.0.1.content.content", RbelUriFacet.class)
        .hasGivenFacetAtPosition("$.body.0.7.content.3.1.content.0", RbelAsn1Facet.class)
        .hasGivenFacetAtPosition("$.body.0.7.content.3.1.content.0", RbelAsn1Facet.class)
        .hasGivenFacetAtPosition(
            "$.body.0.7.content.3.1.content.0.1", RbelAsn1TaggedValueFacet.class)
        .hasGivenValueAtPosition("$.body.0.7.content.3.1.content.0.1.tag", 6)
        .extractChildWithPath("$.body.0.7.content.3.1.content.0.1.content.content")
        .hasStringContentEqualTo("http://ehca.gematik.de/ocsp/")
        // Parse y-coordinate of signature (Nested in BitString)
        .andTheInitialElement()
        .hasGivenValueAtPosition(
            "$.body.2.content.1",
            new BigInteger(
                "9528585714247878020400211740123936754253798904841060501006300662224159406199"));
  }
}
