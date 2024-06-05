/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.testutil.RbelElementAssertion;
import java.nio.file.Files;
import java.nio.file.Paths;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class RbelMimeConverterTest {

  @SneakyThrows
  private static byte[] readMimeMessage() {
    return Files.readAllBytes(Paths.get("src/test/resources/sampleMessages/sampleMail.txt"));
  }

  @Test
  void shouldConvertMimeMessage() {
    byte[] mimeMessage = readMimeMessage();
    String pop3Response = "+OK message follows\r\n"+new String(mimeMessage)+"\r\n.\r\n";
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

  private static RbelElement convertToRbelElement(byte[] input) {
    RbelConfiguration configuration =
        RbelConfiguration.builder().skipParsingWhenMessageLargerThanKb(-1).build();
    return RbelLogger.build(configuration).getRbelConverter().convertElement(input, null);
  }
}
