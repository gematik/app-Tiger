/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerFileSaveInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

@ResetTigerConfiguration
class EpaVauParsingTest {

  @Test
  void shouldAddRecordIdFacetToAllHandshakeMessages() throws IOException {
    try (var tigerProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .fileSaveInfo(
                    TigerFileSaveInfo.builder()
                        .sourceFile("src/test/resources/vauEpa2Flow.tgr")
                        .build())
                .keyFolders(List.of("src/test/resources"))
                .activateRbelParsingFor(List.of("epa-vau", "epa3-vau"))
                .build())) {

      TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessagesWithTimeout(
          tigerProxy, 36, 40);

      final String htmlData =
          RbelHtmlRenderer.render(tigerProxy.getRbelLogger().getMessageHistory());
      FileUtils.writeStringToFile(
          new File("target/vauFlow.html"), htmlData, StandardCharsets.UTF_8);

      assertThat(tigerProxy.getRbelMessagesList().get(24).findElement("$.body.recordId"))
          .get()
          .extracting(RbelElement::getRawStringContent)
          .isEqualTo("X114428539");
      assertThat(tigerProxy.getRbelMessagesList().get(25).findElement("$.body.recordId"))
          .get()
          .extracting(RbelElement::getRawStringContent)
          .isEqualTo("X114428539");

      assertThat(tigerProxy.getRbelMessagesList().get(28).findElement("$.body.recordId"))
          .get()
          .extracting(RbelElement::getRawStringContent)
          .isEqualTo("X114428539");
      assertThat(tigerProxy.getRbelMessagesList().get(29).findElement("$.body.recordId"))
          .get()
          .extracting(RbelElement::getRawStringContent)
          .isEqualTo("X114428539");

      assertThat(tigerProxy.getRbelMessagesList().get(30).findElement("$.body.recordId"))
          .get()
          .extracting(RbelElement::getRawStringContent)
          .isEqualTo("X114428539");
      assertThat(tigerProxy.getRbelMessagesList().get(31).findElement("$.body.recordId"))
          .get()
          .extracting(RbelElement::getRawStringContent)
          .isEqualTo("X114428539");

      assertThat(htmlData)
          .contains("P Header (raw):")
          .contains("01 00 00 00 00 00 00 00 07 00 00 01 07");
    }
  }

  @SneakyThrows
  @Test
  void verifyRiseTraffic() {
    try (var tigerProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .fileSaveInfo(
                    TigerFileSaveInfo.builder()
                        .sourceFile("src/test/resources/rise-vau-log.tgr")
                        .build())
                .activateRbelParsingFor(List.of("epa-vau"))
                .build())) {
      TigerProxyTestHelper.waitUntilMessageListInProxyContainsCountMessagesWithTimeout(
          tigerProxy, 16, 30);

      assertThat(tigerProxy.getRbelMessagesList().get(15).findElement("$.body.recordId"))
          .get()
          .extracting(RbelElement::getRawStringContent)
          .isEqualTo("Y243631459");
    }
  }
}
