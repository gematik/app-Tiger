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

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static j2html.TagCreator.div;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.facets.vau.vau.RbelVauEpaFacet;
import de.gematik.rbellogger.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.key.RbelVauKey;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VauEpaConverterTest {
  private static RbelLogger rbelLogger;

  @BeforeAll
  @SneakyThrows
  static void setUp() {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(final RbelElement element) {
            return element.hasFacet(TestFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              final RbelElement element,
              final Optional<String> key,
              final RbelHtmlRenderingToolkit renderingToolkit) {
            return ancestorTitle()
                .with(
                    vertParentTitle()
                        .with(
                            childBoxNotifTitle(CLS_BODY)
                                .with(t2("Test Facet"))
                                .with(div("Some Notes"))));
          }

          @Override
          public int order() {
            return 100;
          }
        });

    rbelLogger =
        RbelLogger.build(
            new RbelConfiguration()
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                .addPostConversionListener(
                    RbelConverterPlugin.createPlugin(
                        (rbelElement, converter) -> {
                          if (rbelElement.hasFacet(RbelVauEpaFacet.class)) {
                            rbelElement.addFacet(new TestFacet());
                          }
                        }))
                .activateConversionFor("epa-vau")
                .addCapturer(
                    RbelFileReaderCapturer.builder()
                        .rbelFile("src/test/resources/vauFlow.tgr")
                        .build()));
    try (final var capturer = rbelLogger.getRbelCapturer()) {
      capturer.initialize();
    }
  }

  @SneakyThrows
  @Test
  void shouldRenderCleanHtml() {
    assertThat(RbelHtmlRenderer.render(rbelLogger.getMessageHistory())).isNotBlank();
  }

  @Test
  @SneakyThrows
  void shouldParseHandshakeNestedMessage() {
    try (final RbelFileReaderCapturer rbelFileReaderCapturer = getRbelFileReaderCapturer()) {
      final RbelLogger epa2Logger =
          RbelLogger.build(
              new RbelConfiguration()
                  .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                  .addCapturer(rbelFileReaderCapturer));

      rbelFileReaderCapturer.initialize();

      assertThat(epa2Logger.getMessageList().get(24))
          .extractChildWithPath(
              "$.body.Data.content.decoded.AuthorizationAssertion.content.decoded.Assertion.Issuer.text")
          .hasStringContentEqualTo("https://aktor-gateway.gematik.de/authz");
    }
  }

  private static RbelFileReaderCapturer getRbelFileReaderCapturer() {
    return RbelFileReaderCapturer.builder()
        .rbelFile("src/test/resources/vauEp2FlowUnixLineEnding.tgr")
        .build();
  }

  @Test
  @DisplayName(
      "VAU-Flow mit einem \\n"
          + " Zeilenumbruch. \\r"
          + " fehlt, trotzdem soll der Parser das MTOM parsen können")
  @SneakyThrows
  void parseAnotherLogFile() {
    final RbelLogger epa2Logger =
        RbelLogger.build(
            new RbelConfiguration()
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                .activateConversionFor("epa-vau")
                .addCapturer(
                    RbelFileReaderCapturer.builder()
                        .rbelFile("src/test/resources/trafficLog.tgr")
                        .build()));
    try (final var capturer = epa2Logger.getRbelCapturer()) {
      capturer.initialize();
    }

    assertThat(epa2Logger.getMessageList().get(9))
        .extractChildWithPath("$.body.message.reconstructedMessage.Envelope.Header.Action.text")
        .hasStringContentEqualTo("urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b");
  }

  @Test
  @DisplayName("Parse MTOMs with Regex-relevant characters in MTOM-barrier")
  @SneakyThrows
  void parseIbmLogFile() {
    final RbelLogger epa2Logger =
        RbelLogger.build(
            new RbelConfiguration()
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                .activateConversionFor("epa-vau")
                .addCapturer(
                    RbelFileReaderCapturer.builder()
                        .rbelFile("src/test/resources/mtomVauTraffic.tgr")
                        .build()));
    try (final var capturer = epa2Logger.getRbelCapturer()) {
      capturer.initialize();
    }

    assertThat(epa2Logger.getMessageList().get(5))
        .extractChildWithPath("$.body.message.reconstructedMessage.Envelope.Header.Action.text")
        .hasStringContentEqualTo("urn:ihe:iti:2007:RetrieveDocumentSetResponse");
  }

  @Test
  void nestedHandshakeMessage_ShouldParseNestedJson() {
    assertThat(rbelLogger.getMessageHistory()).hasSize(8);

    assertThat(rbelLogger.getMessageHistory().getFirst())
        .extractChildWithPath("$.body.Data.content.decoded.DataType.content")
        .hasStringContentEqualTo("VAUClientHelloData");
  }

  @Test
  void vauClientSigFin_shouldDecipherMessageWithCorrectKeyId() {
    assertThat(rbelLogger.getMessageList().get(2))
        .extractChildWithPath("$.body.FinishedData.content.keyId")
        .hasStringContentEqualTo(
            "f787a8db0b2e0d7c418ea20aba6125349871dfe36ab0f60a3d55bf4d1b556023");
  }

  @Test
  void clientPayload_shouldParseEncapsulatedXml() {
    assertThat(rbelLogger.getMessageList().get(4))
        .extractChildWithPath("$.body.message.Envelope.Body.sayHello.arg0.text")
        .hasStringContentEqualTo("hello from integration client");
  }

  @Test
  void parentKeysForVauKeysShouldBeCorrect() {
    assertThat(rbelLogger.getMessageList().get(7))
        .extractChildWithPath("$.body")
        .extractFacet(RbelVauEpaFacet.class)
        .extracting(facet -> facet.getKeyUsed().get())
        .isInstanceOf(RbelVauKey.class)
        .extracting(key -> ((RbelVauKey) key).getParentKey())
        .extracting(RbelKey::getKeyName)
        .isEqualTo("prk_vauClientKeyPair");
  }

  private static class TestFacet implements RbelFacet {
    @Override
    public RbelMultiMap getChildElements() {
      return new RbelMultiMap();
    }
  }
}
