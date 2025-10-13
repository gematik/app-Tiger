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
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.facets.xml.RbelXmlAttributeFacet;
import de.gematik.rbellogger.facets.xml.RbelXmlFacet;
import de.gematik.rbellogger.facets.xml.RbelXmlNamespaceFacet;
import de.gematik.rbellogger.facets.xml.RbelXmlProcessingInstructionFacet;
import de.gematik.rbellogger.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.util.RbelSocketAddress;
import java.io.File;
import java.io.IOException;
import java.util.List;
import lombok.val;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class XmlConverterTest {

  private String curlMessage;
  private String curlMessageHtml;
  private String xmlFile;

  @BeforeEach
  void setUp() throws IOException {
    curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/xmlMessage.curl");
    curlMessageHtml =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/htmlMessage.curl");
  }

  @Test
  void shouldRenderCleanHtml() throws IOException {
    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);
    convertedMessage.addFacet(
        RbelTcpIpMessageFacet.builder()
            .receiver(
                RbelElement.wrap(null, convertedMessage, RbelSocketAddress.create("recipient", 1)))
            .sender(RbelElement.wrap(null, convertedMessage, RbelSocketAddress.create("sender", 1)))
            .build());

    FileUtils.writeStringToFile(
        new File("target/xmlNested.html"), RbelHtmlRenderer.render(List.of(convertedMessage)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"ISO-8859-15", "UTF-8", "UTF-16", "windows-1252"})
  void advancedHtml(String charset) throws IOException {
    xmlFile =
        """
        <?xml version="1.0" encoding="%s"?>
        <MP>
        	<P blub="löäopüß"/>
        </MP>
        """
            .formatted(charset);
    final RbelElement convertedMessage =
        RbelLogger.build()
            .getRbelConverter()
            .convertElement(
                wrapInHttpRequestWithContentType("text/html; charset=" + charset, xmlFile), null);
    convertedMessage.addFacet(
        RbelTcpIpMessageFacet.builder()
            .receiver(
                RbelElement.wrap(null, convertedMessage, RbelSocketAddress.create("recipient", 1)))
            .sender(RbelElement.wrap(null, convertedMessage, RbelSocketAddress.create("sender", 1)))
            .build());

    final String render = RbelHtmlRenderer.render(List.of(convertedMessage));
    assertThat(render)
        .contains("encoding=&quot;" + charset + "&quot;")
        .contains("blub=&quot;löäopüß&quot;");
    if (!charset.equals("UTF-8")) {
      assertThat(render).doesNotContain("encoding=&quot;UTF-8&quot;");
    }
    FileUtils.writeStringToFile(new File("target/cleanHtml.html"), render);
  }

  private byte[] wrapInHttpRequestWithContentType(String contentType, String content) {
    val header =
        """
        POST / HTTP/1.1\r
        Host: localhost\r
        Content-Type: %s\r
        Content-Length: %d\r
        \r
        """
            .formatted(contentType, content.getBytes().length);
    return (header + content).getBytes();
  }

  @Test
  void convertMessage_shouldGiveHtmlBody() {
    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessageHtml, null);

    assertThat(convertedMessage)
        .hasGivenFacetAtPosition("$.body", RbelXmlFacet.class)
        .hasStringContentEqualToAtPosition("$.body.html.head.link.href", "jetty-dir.css");
  }

  @Test
  void convertMessage_shouldGiveXmlBody() {
    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);

    assertThat(convertedMessage).extractChildWithPath("$.body").hasFacet(RbelXmlFacet.class);
  }

  @Test
  void retrieveXmlAttribute_shouldReturnAttributeWithContent() {
    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);

    assertThat(convertedMessage)
        .andPrintTree()
        .hasStringContentEqualToAtPosition(
            "$.body.RegistryResponse.status",
            "urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Failure");
  }

  @Test
  void retrieveListMemberAttribute() {
    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);

    final List<RbelElement> deepPathResults =
        convertedMessage.findRbelPathMembers(
            "$.body.RegistryResponse.RegistryErrorList.RegistryError[0].errorCode");
    assertThat(convertedMessage.findRbelPathMembers("$..RegistryError.errorCode"))
        .containsAll(deepPathResults);

    assertThat(deepPathResults.get(0).getRawStringContent())
        .isEqualTo("XDSDuplicateUniqueIdInRegistry");
  }

  @Test
  void shouldConserveMemberOrder() {
    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);

    assertThat(convertedMessage)
        .extractChildWithPath("$.body.RegistryResponse.RegistryErrorList.RegistryError[0]")
        .hasStringContentEqualToAtPosition("$.*[0]", "XDSDuplicateUniqueIdInRegistry")
        .hasStringContentEqualToAtPosition(
            "$.*[1]", "urn:oasis:names:tc:ebxml-regrep:ErrorSeverityType:Warning")
        .andTheInitialElement()
        .extractChildWithPath("$.body.RegistryResponse.RegistryErrorList.*[0]")
        .asString()
        .isEqualToIgnoringWhitespace("foo");
    assertThat(convertedMessage)
        .extractChildWithPath("$.body.RegistryResponse.RegistryErrorList.*[2]")
        .asString()
        .isEqualToIgnoringWhitespace("bar");
  }

  @RepeatedTest(10)
  // repeated since this is a test very sensitive to wrong element ordering. It is our canary in
  // case we screw up the element ordering while parsing!
  void retrieveTextContent() {
    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);

    final List<RbelElement> rbelPathResult =
        convertedMessage.findRbelPathMembers("$..RegistryError[0].text");

    assertThat(rbelPathResult).hasSize(1);
    assertThat(rbelPathResult.get(0).getRawStringContent().trim()).isEqualTo("text in element");
  }

  @Test
  void diveIntoNestedJwt() {
    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);

    final List<RbelElement> rbelPathResult =
        convertedMessage.findRbelPathMembers("$..jwtTag.text.body.scopes_supported.0");

    assertThat(rbelPathResult).hasSize(1);
    assertThat(rbelPathResult.get(0).getRawStringContent().trim()).isEqualTo("openid");
  }

  @Test
  void retrieveEmptyTextContent() {
    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);

    final List<RbelElement> rbelPathResult =
        convertedMessage.findRbelPathMembers("$..textTest.text");

    assertThat(rbelPathResult).hasSize(1);
    assertThat(rbelPathResult.get(0).getRawStringContent()).isEmpty();
  }

  @Test
  void retrieveUrlAsTextContent() {
    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);

    final List<RbelElement> rbelPathResult =
        convertedMessage.findRbelPathMembers("$..urlText.text");

    assertThat(rbelPathResult).hasSize(1);
    assertThat(rbelPathResult.get(0).getRawStringContent()).isEqualTo("http://url.text.de");
  }

  @Test
  void longNestedTextContent() throws IOException {
    final RbelElement convertedMessage =
        RbelLogger.build()
            .getRbelConverter()
            .convertElement(
                readCurlFromFileWithCorrectedLineBreaks(
                    "src/test/resources/XmlWithLongTextNode.curl"),
                null);

    final List<RbelElement> rbelPathResult =
        convertedMessage.findRbelPathMembers(
            "$.body.Envelope.Body.SignDocumentResponse.SignResponse.SignatureObject.Base64Signature.text");

    assertThat(rbelPathResult).hasSize(1);
    assertThat(rbelPathResult.get(0).getRawStringContent()).hasSize(40920);
  }

  @Test
  void namespacesShouldBeCorrectlyParsedAndStored() {
    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);

    assertThat(convertedMessage)
        .extractChildWithPath("$.body.RegistryResponse.xmlns:ns")
        .hasStringContentEqualTo("urn:oasis:names:tc:ebxml-regrep:xsd:rim:3.0")
        .hasFacet(RbelXmlAttributeFacet.class)
        .hasFacet(RbelXmlNamespaceFacet.class);
  }

  @Test
  void xmlProcessingInstructions() throws Exception {
    var rbelLogger =
        RbelLogger.build(
            new RbelConfiguration()
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                .activateConversionFor("epa3-vau")
                .addCapturer(
                    RbelFileReaderCapturer.builder()
                        .rbelFile("src/test/resources/xmlProcessingInstructions.tgr")
                        .build()));
    try (final var capturer = rbelLogger.getRbelCapturer()) {
      capturer.initialize();
    }

    assertThat(rbelLogger.getMessageList().get(14).findRbelPathMembers("$..xml-stylesheet").get(0))
        .hasStringContentEqualTo("<?xml-stylesheet type=\"text/xsl\" href=\"vhitg-cda-v3.xsl\"?>")
        .hasFacet(RbelXmlProcessingInstructionFacet.class)
        .extractChildWithPath("$.href")
        .hasStringContentEqualTo("vhitg-cda-v3.xsl")
        .andTheInitialElement()
        .extractChildWithPath("$.type")
        .hasStringContentEqualTo("text/xsl");
  }
}
