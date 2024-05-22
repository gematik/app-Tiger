/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class XmlConverterTest {

  private String curlMessage;
  private String curlMessageHtml;

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
            .receiver(RbelElement.wrap(null, convertedMessage, new RbelHostname("recipient", 1)))
            .sender(RbelElement.wrap(null, convertedMessage, new RbelHostname("sender", 1)))
            .build());

    FileUtils.writeStringToFile(
        new File("target/xmlNested.html"), RbelHtmlRenderer.render(List.of(convertedMessage)));
  }

  @Test
  void convertMessage_shouldGiveHtmlBody() {
    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessageHtml, null);

    assertThat(convertedMessage.findElement("$.body").get().hasFacet(RbelXmlFacet.class)).isTrue();

    assertThat(convertedMessage.findElement("$.body.html.head.link.href"))
        .get()
        .extracting(RbelElement::getRawStringContent)
        .isEqualTo("jetty-dir.css");
  }

  @Test
  void convertMessage_shouldGiveXmlBody() {
    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);

    assertThat(convertedMessage.findRbelPathMembers("$.body").get(0).hasFacet(RbelXmlFacet.class))
        .isTrue();
  }

  @Test
  void retrieveXmlAttribute_shouldReturnAttributeWithContent() {
    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);

    assertThat(
            convertedMessage
                .findRbelPathMembers("$.body.RegistryResponse.status")
                .get(0)
                .getRawStringContent())
        .isEqualTo("urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Failure");
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

    final RbelElement registryResponseNode =
        convertedMessage
            .findRbelPathMembers("$.body.RegistryResponse.RegistryErrorList.RegistryError[0]")
            .get(0);
    List<String> childNodeTextInOrder =
        registryResponseNode.getChildNodes().stream()
            .map(RbelElement::getRawStringContent)
            .collect(Collectors.toList());

    assertThat(childNodeTextInOrder.get(0)).isEqualTo("XDSDuplicateUniqueIdInRegistry");
    assertThat(childNodeTextInOrder.get(1))
        .isEqualTo("urn:oasis:names:tc:ebxml-regrep:ErrorSeverityType:Warning");

    final RbelElement registryErrorList =
        convertedMessage.findRbelPathMembers("$.body.RegistryResponse.RegistryErrorList").get(0);
    childNodeTextInOrder =
        registryErrorList.getChildNodes().stream()
            .map(RbelElement::getRawStringContent)
            .collect(Collectors.toList());

    assertThat(childNodeTextInOrder.get(0).trim()).isEqualTo("foo");
    assertThat(childNodeTextInOrder.get(2).trim()).isEqualTo("bar");
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
                .setActivateVauEpa3Parsing(true)
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
