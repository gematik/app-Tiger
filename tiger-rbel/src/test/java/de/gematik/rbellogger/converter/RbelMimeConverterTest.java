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

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.testutil.RbelElementAssertion;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
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
    RbelConfiguration configuration =
        RbelConfiguration.builder()
            .skipParsingWhenMessageLargerThanKb(-1)
            .build()
            .activateConversionFor("mime")
            .activateConversionFor("pop3");
    for (String user : List.of("user1", "user2")) {
      configuration.addInitializer(
          new RbelKeyFolderInitializer("src/test/resources/example_mail/" + user));
    }
    converter = RbelLogger.build(configuration).getRbelConverter();
  }

  @SneakyThrows
  private static byte[] readMimeMessage(String path) {
    return Files.readAllBytes(Paths.get("src/test/resources/" + path));
  }

  @Test
  void shouldConvertMimeMessage() {
    final byte[] mimeMessage = readMimeMessage("sampleMessages/sampleMail.txt");
    final String pop3Response = getPop3Response(mimeMessage);
    final var element = convertPop3RetrResponse(pop3Response);
    RbelElementAssertion.assertThat(element)
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
    var origElement =
        convertMessagePair("RETR 1\r\n", origPop3Response).findElement("$.pop3Body").get();

    final RbelElement decryptedElement =
        convertMessagePair("RETR 1\r\n", encryptedPop3Response)
            .findElement("$.pop3Body.mimeBody.decrypted.mimeBody")
            .get();

    var originalBodyContent = origElement.findElement("$.mimeBody").get().getRawStringContent();

    RbelElementAssertion.assertThat(decryptedElement)
        .extractChildWithPath("$.mimeBody")
        .hasStringContentEqualTo(originalBodyContent);
  }

  @Test
  void shouldRenderMimeContent() throws IOException {
    final byte[] mimeMessage = readMimeMessage("sampleMessages/sampleMail.txt");
    final String pop3Message = getPop3Response(mimeMessage);
    final RbelElement convertedMessage = convertPop3RetrResponse(pop3Message);

    assertHtmlRendering(convertedMessage);
  }

  private static AbstractStringAssert<?> assertHtmlRendering(RbelElement convertedMessage)
      throws IOException {
    final String convertedHtml = RbelHtmlRenderer.render(List.of(convertedMessage));
    FileUtils.writeStringToFile(
        new File("target/directHtml.html"), convertedHtml, StandardCharsets.UTF_8);

    return Assertions.assertThat(convertedHtml)
        .contains("Mime Message:")
        .contains("Mime Headers:")
        .contains("Mime Body:");
  }

  @Test
  void shouldRenderTopMimeContent() throws IOException {
    final byte[] mimeMessage = readMimeMessage("sampleMessages/sampleMail.txt");
    final String pop3Message = "+OK\r\n" + new String(mimeMessage) + "\r\n.\r\n";
    final RbelElement convertedMessage = convertMessagePair("TOP 1 10\r\n", pop3Message);

    assertHtmlRendering(convertedMessage);
  }

  @Test
  void shouldRenderEncryptedMimeContent() throws IOException {
    final byte[] mimeMessage = readMimeMessage("example_mail/05_msgReceived-00.eml");
    final String pop3Message = getPop3Response(mimeMessage);
    final RbelElement convertedMessage = convertPop3RetrResponse(pop3Message);

    assertHtmlRendering(convertedMessage).contains("Decrypted Message:");
  }
}
