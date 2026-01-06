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

import static de.gematik.rbellogger.facets.websocket.RbelWebsocketFrameType.CLOSE_FRAME;
import static de.gematik.rbellogger.facets.websocket.RbelWebsocketFrameType.DATA_FRAME;
import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.websocket.RbelSockJsFacet;
import de.gematik.rbellogger.facets.websocket.RbelStompFacet;
import de.gematik.rbellogger.facets.websocket.RbelWebsocketHandshakeFacet;
import de.gematik.rbellogger.facets.websocket.RbelWebsocketMessageFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WebsocketConverterTest {
  private static RbelLogger rbelLogger;

  @BeforeAll
  @SneakyThrows
  static void setUp() {
    rbelLogger =
        RbelLogger.build(
            new RbelConfiguration()
                .setActivateRbelParsingFor(List.of("websocket"))
                .addCapturer(
                    RbelFileReaderCapturer.builder()
                        .rbelFile("src/test/resources/websocket.tgr")
                        .build()));
    try (final var capturer = rbelLogger.getRbelCapturer()) {
      capturer.initialize();
    }
  }

  @SneakyThrows
  @Test
  void shouldRenderCleanHtml() {
    final String html = RbelHtmlRenderer.render(rbelLogger.getMessageHistory());
    Files.write(new File("target/websocket.html").toPath(), html.getBytes());
    System.out.println(rbelLogger.getMessageList().get(12).printTreeStructure());
    assertThat(html)
        .isNotBlank()
        .contains("SUBSCRIBE\\ndestination:/topic/data\\nid:1")
        .contains("Flags: ");
  }

  @SneakyThrows
  @Test
  void checkWebsocketHandshakeMessages() {
    assertThat(rbelLogger.getMessageList().get(2))
        .hasFacet(RbelWebsocketHandshakeFacet.class)
        .hasStringContentEqualToAtPosition("$.header.[~'connection']", "upgrade")
        .hasStringContentEqualToAtPosition("$.header.[~'upgrade']", "websocket");
    assertThat(rbelLogger.getMessageList().get(3))
        .hasFacet(RbelWebsocketHandshakeFacet.class)
        .hasStringContentEqualToAtPosition("$.responseCode", "101")
        .hasStringContentEqualToAtPosition("$.header.[~'connection']", "upgrade")
        .hasStringContentEqualToAtPosition("$.header.[~'upgrade']", "websocket");
  }

  @SneakyThrows
  @Test
  void checkWebsocketTrees() {
    assertThat(rbelLogger.getMessageList().get(6))
        .hasFacet(RbelWebsocketMessageFacet.class)
        .hasGivenValueAtPosition("$.opcode", 1)
        .hasGivenValueAtPosition("$.payloadLength", 1)
        .hasGivenValueAtPosition("$.masked", false)
        .hasGivenValueAtPosition("$.frameType", DATA_FRAME)
        .hasStringContentEqualToAtPosition("$.payload", "o");
    assertThat(rbelLogger.getMessageList().get(7))
        .hasFacet(RbelWebsocketMessageFacet.class)
        .hasGivenValueAtPosition("$.opcode", 1)
        .hasGivenValueAtPosition("$.payloadLength", 61)
        .hasGivenValueAtPosition("$.masked", true)
        .hasGivenValueAtPosition("$.frameType", DATA_FRAME)
        .extractChildWithPath("$.payload")
        .asString()
        .startsWith("[\"CONNECT\\nheart-beat:");
    assertThat(rbelLogger.getMessageList().get(13))
        .andPrintTree()
        .hasFacet(RbelWebsocketMessageFacet.class)
        .hasGivenValueAtPosition("$.opcode", 1)
        .hasGivenValueAtPosition("$.masked", false)
        .extractChildWithPath("$.payload")
        .hasFacet(RbelSockJsFacet.class)
        .extractChildWithPath("$.content.0.content")
        .hasFacet(RbelStompFacet.class);
  }

  @SneakyThrows
  @Test
  void checkWebsocketExchangeWithCompression() {
    var rbelLogger =
        RbelLogger.build(
            new RbelConfiguration()
                .setActivateRbelParsingFor(List.of("websocket"))
                .addCapturer(
                    RbelFileReaderCapturer.builder()
                        .rbelFile("src/test/resources/testhub.tgr")
                        .build()));
    try (final var capturer = rbelLogger.getRbelCapturer()) {
      capturer.initialize();
    }

    rbelLogger.getMessageList().stream().map(RbelElement::printTreeStructureWithoutColors).toList();
    final String html = RbelHtmlRenderer.render(rbelLogger.getMessageHistory());
    Files.write(new File("target/wsCompression.html").toPath(), html.getBytes());

    assertThat(rbelLogger.getMessageList().get(17))
        .hasGivenValueAtPosition("$.opcode", 1)
        .hasGivenValueAtPosition("$.frameType", DATA_FRAME);

    assertThat(rbelLogger.getMessageList().get(18))
        .hasGivenValueAtPosition("$.opcode", 1)
        .hasGivenValueAtPosition("$.frameType", DATA_FRAME);

    assertThat(rbelLogger.getMessageList().get(19))
        .hasGivenValueAtPosition("$.opcode", 8)
        .hasGivenValueAtPosition("$.frameType", CLOSE_FRAME);

    assertThat(rbelLogger.getMessageList().get(17).printShortDescription())
        .startsWith("WebSocket(<-): '{\"type\":\"StandardScenario");
    assertThat(rbelLogger.getMessageList().get(18).printShortDescription())
        .startsWith("WebSocket(<-): '{\"type\":\"Token");
    assertThat(rbelLogger.getMessageList().get(19).printShortDescription())
        .startsWith("WebSocket(<-): CLOSE_FRAME");
  }
}
