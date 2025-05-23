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
package de.gematik.test.tiger.proxy;

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerFileSaveInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.tls.vau.VauSessionFacet;
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

      // VAUClientHello
      assertThatMessageNumberXXContainsVauSessionFacetWithCorrectRecordId(tigerProxy, 24);
      // VAUServerHello
      assertThatMessageNumberXXContainsVauSessionFacetWithCorrectRecordId(tigerProxy, 25);
      // VAUClientSigFin
      assertThatMessageNumberXXContainsVauSessionFacetWithCorrectRecordId(tigerProxy, 28);
      // VAUServerFin
      assertThatMessageNumberXXContainsVauSessionFacetWithCorrectRecordId(tigerProxy, 29);
      // VAU encrypted client request
      assertThatMessageNumberXXContainsVauSessionFacetWithCorrectRecordId(tigerProxy, 30);
      // VAU encrypted server response
      assertThatMessageNumberXXContainsVauSessionFacetWithCorrectRecordId(tigerProxy, 31);

      assertThat(htmlData)
          .contains("P Header (raw):")
          .contains("01 00 00 00 00 00 00 00 07 00 00 01 07");
    }
  }

  private static void assertThatMessageNumberXXContainsVauSessionFacetWithCorrectRecordId(
      TigerProxy tigerProxy, int index) {
    assertThat(tigerProxy.getRbelMessagesList().get(index))
        .extractChildWithPath("$.body")
        .hasFacet(VauSessionFacet.class)
        .hasStringContentEqualToAtPosition("$.recordId", "X114428539");
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
