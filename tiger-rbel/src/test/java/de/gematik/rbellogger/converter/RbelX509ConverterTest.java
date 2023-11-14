/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RbelX509ConverterTest {

  private RbelElement xmlMessage;

  @BeforeEach
  public void setUp() throws IOException {
    xmlMessage =
        RbelLogger.build()
            .getRbelConverter()
            .parseMessage(
                readCurlFromFileWithCorrectedLineBreaks(
                        "src/test/resources/sampleMessages/xmlMessage.curl")
                    .getBytes(),
                null,
                null,
                Optional.of(ZonedDateTime.now()));
  }

  @SneakyThrows
  @Test
  void shouldRenderCleanHtml() {
    assertThat(RbelHtmlRenderer.render(List.of(xmlMessage))).isNotBlank();
  }

  @SneakyThrows
  @Test
  void shouldBeAccessibleViaRbelPath() {
    final RbelElement certificateElement =
        xmlMessage.findElement("$..[?(@.subject=~'.*TEST-ONLY.*')]").get();

    assertThat(certificateElement)
        .isEqualTo(
            xmlMessage
                .findElement(
                    "$.body.RegistryResponse.RegistryErrorList.RegistryError.jwtTag.text.header.x5c.0.content")
                .get());
  }

  @SneakyThrows
  @Test
  void shouldParseX500ContentAsWell() {
    assertThat(xmlMessage.findElement("$..subject.CN").get().getRawStringContent())
        .isEqualTo("IDP Sig 3");
  }
}
