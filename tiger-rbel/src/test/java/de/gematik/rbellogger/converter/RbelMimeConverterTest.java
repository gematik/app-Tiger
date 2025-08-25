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

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.rbellogger.facets.jackson.RbelJsonFacet;
import de.gematik.rbellogger.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RbelMimeConverterTest extends AbstractResponseConverterTest {

  @BeforeEach
  void init() {
    RbelConfiguration configuration = createMimePop3ConverterConfiguration();
    for (String user : List.of("user1", "user2")) {
      configuration.addInitializer(
          new RbelKeyFolderInitializer("src/test/resources/example_mail/" + user));
    }
    converter = RbelLogger.build(configuration).getRbelConverter();
  }

  private static RbelConfiguration createMimePop3ConverterConfiguration() {
    return RbelConfiguration.builder()
        .skipParsingWhenMessageLargerThanKb(-1)
        .build()
        .activateConversionFor("mime")
        .activateConversionFor("pop3");
    //        .activateConversionFor("X509");
  }

  @SneakyThrows
  private static byte[] readMimeMessage(String path) {
    return Files.readAllBytes(Paths.get("src/test/resources/" + path));
  }

  @Test
  void shouldConvertMimeMessage() {
    final byte[] mimeMessage = readMimeMessage("sampleMessages/sampleMail.txt");
    final String pop3Response = getPop3Response(mimeMessage);

    convertToRbelElement("+OK greeting\r\n");
    final var element = convertPop3RetrResponse(pop3Response);
    assertThat(element)
        .extractChildWithPath("$.pop3Body")
        .hasChildWithPath("$.mimeHeader")
        .hasChildWithPath("$.mimeBody")
        .hasChildWithPath("$.mimeBody.preamble")
        .hasChildWithPath("$.mimeBody.parts")
        .hasChildWithPath("$.mimeBody.parts.0.mimeHeader")
        .hasChildWithPath("$.mimeBody.parts.0.mimeBody")
        .hasChildWithPath("$.mimeBody.parts.1.mimeHeader")
        .hasChildWithPath("$.mimeBody.parts.1.mimeBody")
        .doesNotHaveChildWithPath("$.mimeBody.epilogue")
        .extractChildWithPath("$.mimeBody.parts.0.mimeBody.foo")
        .hasStringContentEqualTo("bar");
  }

  private RbelElement convertPop3RetrResponse(String pop3Response) {
    return convertMessagePair("RETR 1\r\n", pop3Response);
  }

  private static String getPop3Response(byte[] mimeMessage) {
    return "+OK message follows\r\n" + new String(mimeMessage) + "\r\n.\r\n";
  }

  @ParameterizedTest
  @ValueSource(strings = {"05_msgReceived-00.eml", "05_msgReceived-01.eml"})
  void shouldDecryptMessageBody(String mailName) {
    final byte[] encryptedMessage = readMimeMessage("example_mail/" + mailName);
    final String encryptedPop3Response = getPop3Response(encryptedMessage);
    final byte[] origMessage = readMimeMessage("example_mail/01_origMessage_VERIFY.eml");
    final String origPop3Response = getPop3Response(origMessage);

    convertToRbelElement("+OK greeting\r\n");
    var origElement =
        convertMessagePair("RETR 1\r\n", origPop3Response).findElement("$.pop3Body").get();

    RbelElement rbelElement = convertMessagePair("RETR 1\r\n", encryptedPop3Response);

    var originalBodyContent = origElement.findElement("$.mimeBody").get().getRawStringContent();

    assertThat(rbelElement)
        .extractChildWithPath("$.pop3Body.mimeBody.decrypted.mimeBody.signed.mimeBody.mimeBody")
        .hasStringContentEqualTo(originalBodyContent);

    assertThat(rbelElement)
        .extractChildWithPath("$.pop3Body.mimeBody.decrypted.mimeBody.signerInfos.0")
        .hasStringContentEqualToAtPosition("$.contentType.name", "data")
        .hasStringContentEqualToAtPosition("$.digestAlgorithm.name", "sha-256")
        .hasStringContentEqualToAtPosition("$.encryptionAlgorithm.name", "rsaPSS")
        .hasChildWithPath("$.signerId.issuer")
        .hasChildWithPath("$.signerId.serialNumber")
        .hasChildWithPath("$.signature")
        .hasChildWithPath("$.signedAttributes.recipientEmails.0.emailAddress")
        .hasChildWithPath("$.signedAttributes.recipientEmails.0.recipientId");

    assertThat(rbelElement)
        .extractChildWithPath("$.pop3Body.mimeBody")
        .hasChildWithPath("$.recipientInfos.0.recipientId.serialNumber")
        .hasChildWithPath("$.unauthAttributes.recipientEmails.0.emailAddress");
  }

  @ParameterizedTest
  @ValueSource(strings = {"05_msgReceived-00.eml", "05_msgReceived-01.eml"})
  void shouldFailDecryptInvalidMessageBody(String mailName) {
    final byte[] encryptedMessage = readMimeMessage("example_mail/" + mailName);
    final byte[] trimmedEncryptedMessage =
        Arrays.copyOf(encryptedMessage, encryptedMessage.length - 200);
    final String encryptedPop3Response = getPop3Response(trimmedEncryptedMessage);

    convertToRbelElement("+OK greeting\r\n");
    final RbelElement mimeBody =
        convertMessagePair("RETR 1\r\n", encryptedPop3Response)
            .findElement("$.pop3Body.mimeBody")
            .get();

    assertThat(mimeBody).hasFacet(RbelNoteFacet.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"05_msgReceived-00.eml", "05_msgReceived-01.eml"})
  void shouldFailDecryptMessageWithoutKeys(String mailName) {
    final byte[] encryptedMessage = readMimeMessage("example_mail/" + mailName);
    final String encryptedPop3Response = getPop3Response(encryptedMessage);

    RbelConfiguration configuration = createMimePop3ConverterConfiguration();
    converter = RbelLogger.build(configuration).getRbelConverter();

    convertToRbelElement("+OK greeting\r\n");

    final RbelElement mimeBody =
        convertMessagePair("RETR 1\r\n", encryptedPop3Response)
            .findElement("$.pop3Body.mimeBody")
            .get();

    assertThat(mimeBody).hasFacet(RbelNoteFacet.class);
  }

  @Test
  void shouldRenderMimeContent() throws IOException {
    final byte[] mimeMessage = readMimeMessage("sampleMessages/sampleMail.txt");
    final String pop3Message = getPop3Response(mimeMessage);

    convertToRbelElement("+OK greeting\r\n");
    final RbelElement convertedMessage = convertPop3RetrResponse(pop3Message);

    assertHtmlRendering(convertedMessage, "Mime Message:", "Mime Headers:", "Mime Body:");
  }

  private static AbstractStringAssert<?> assertHtmlRendering(
      RbelElement convertedMessage, String... expected) throws IOException {
    var renderer = new RbelHtmlRenderer();
    renderer.setMaximumEntitySizeInBytes(3000);
    var convertedHtml = renderer.doRender(List.of(convertedMessage));
    FileUtils.writeStringToFile(
        new File("target/directHtml.html"), convertedHtml, StandardCharsets.UTF_8);

    return Assertions.assertThat(convertedHtml).contains(expected);
  }

  @Test
  void shouldRenderTopMimeContent() throws IOException {
    final byte[] mimeMessage = readMimeMessage("sampleMessages/sampleMail.txt");
    final String pop3Message = "+OK\r\n" + new String(mimeMessage) + "\r\n.\r\n";

    convertToRbelElement("+OK greeting\r\n");
    final RbelElement convertedMessage = convertMessagePair("TOP 1 10\r\n", pop3Message);

    assertHtmlRendering(convertedMessage, "Mime Message:", "Mime Headers:", "Mime Body:");
  }

  @Test
  void shouldRenderEncryptedMimeContent() throws IOException {
    final byte[] mimeMessage = readMimeMessage("example_mail/05_msgReceived-00.eml");
    final String pop3Message = getPop3Response(mimeMessage);

    convertToRbelElement("+OK greeting\r\n");
    final RbelElement convertedMessage = convertPop3RetrResponse(pop3Message);

    assertHtmlRendering(
        convertedMessage,
        "Decrypted Message:",
        "Signed Message:",
        "rfc822",
        "Recipient Email Addresses",
        "Recipient Info",
        "Recipient Identifier",
        "Signer Info",
        "Signer Identifier",
        "redacted");

    assertThat(convertedMessage)
        .hasGivenFacetAtPosition(
            "$.pop3Body.mimeBody.decrypted.mimeBody.signed.mimeBody.mimeBody", RbelJsonFacet.class);
  }
}
