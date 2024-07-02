/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class RbelMimeConverterTest {

  @SneakyThrows
  private static byte[] readMimeMessage(String path) {
    return Files.readAllBytes(Paths.get("src/test/resources/" + path));
  }

  @Test
  void shouldConvertMimeMessage() {
    byte[] mimeMessage = readMimeMessage("sampleMessages/sampleMail.txt");
    String pop3Response = getPop3Response(mimeMessage);
    var element = convertToRbelElement(pop3Response.getBytes());
    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.body")
        .hasChildWithPath("$.header")
        .hasChildWithPath("$.body")
        .hasChildWithPath("$.body.preamble")
        .hasChildWithPath("$.body.parts")
        .hasChildWithPath("$.body.parts.0.header")
        .hasChildWithPath("$.body.parts.0.body")
        .hasChildWithPath("$.body.parts.1.header")
        .hasChildWithPath("$.body.parts.1.body")
        .doesNotHaveChildWithPath("$.body.epilogue");
  }

  private static String getPop3Response(byte[] mimeMessage) {
    return "+OK message follows\r\n" + new String(mimeMessage) + "\r\n.\r\n";
  }

  @Test
  void shouldDecryptMessageBody() {
    byte[] encryptedMessage = readMimeMessage("example_mail/05_msgReceived-00.eml");
    String encryptedPop3Response = getPop3Response(encryptedMessage);
    byte[] origMessage = readMimeMessage("example_mail/01_origMessage_VERIFY.eml");
    String origPop3Response = getPop3Response(origMessage);
    var origElement = convertToRbelElement(origPop3Response.getBytes()).findElement("$.body").get();

    RbelElement decryptedElement =
        convertToRbelElement(encryptedPop3Response.getBytes())
            .findElement("$.body.body.decrypted.body")
            .get();

    var originalBodyContent = origElement.findElement("$body").get().getRawStringContent();

    RbelElementAssertion.assertThat(decryptedElement)
        .extractChildWithPath("$.body")
        .hasStringContentEqualTo(originalBodyContent);
  }

  @Test
  void shouldRenderMimeContent() throws IOException {
    byte[] mimeMessage = readMimeMessage("sampleMessages/sampleMail.txt");
    String pop3Message = getPop3Response(mimeMessage);
    final RbelElement convertedMessage =
        convertToRbelElement(pop3Message.getBytes(StandardCharsets.UTF_8));

    final String convertedHtml = RbelHtmlRenderer.render(List.of(convertedMessage));
    FileUtils.writeStringToFile(
        new File("target/directHtml.html"), convertedHtml, StandardCharsets.UTF_8);

    Assertions.assertThat(convertedHtml)
        .contains("Mime Message:")
        .contains("Mime Headers:")
        .contains("Mime Body:");
  }

  @Test
  void shouldRenderEncryptedMimeContent() throws IOException {
    byte[] mimeMessage = readMimeMessage("example_mail/05_msgReceived-00.eml");
    String pop3Message = getPop3Response(mimeMessage);
    final RbelElement convertedMessage =
        convertToRbelElement(pop3Message.getBytes(StandardCharsets.UTF_8));

    final String convertedHtml = RbelHtmlRenderer.render(List.of(convertedMessage));
    FileUtils.writeStringToFile(
        new File("target/directHtml.html"), convertedHtml, StandardCharsets.UTF_8);

    Assertions.assertThat(convertedHtml)
        .contains("Mime Message:")
        .contains("Mime Headers:")
        .contains("Mime Body:")
        .contains("Decrypted Message:");
  }

  private static RbelElement convertToRbelElement(byte[] input) {
    RbelConfiguration configuration =
        RbelConfiguration.builder().skipParsingWhenMessageLargerThanKb(-1).build();
    configuration.setActivateAsn1Parsing(false);
    for (String user : List.of("user1", "user2")) {
      configuration.addInitializer(
          new RbelKeyFolderInitializer("src/test/resources/example_mail/" + user));
    }
    return RbelLogger.build(configuration).getRbelConverter().convertElement(input, null);
  }
}
