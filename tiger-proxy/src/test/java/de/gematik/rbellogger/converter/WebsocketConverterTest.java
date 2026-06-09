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
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.core.RbelResponseFacet;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.facets.http.RbelHttpMessageFacet;
import de.gematik.rbellogger.facets.websocket.RbelSockJsFacet;
import de.gematik.rbellogger.facets.websocket.RbelStompFacet;
import de.gematik.rbellogger.facets.websocket.RbelWebsocketHandshakeFacet;
import de.gematik.rbellogger.facets.websocket.RbelWebsocketMessageFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.util.RbelSocketAddress;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@Slf4j
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
    final String html = RbelHtmlRenderer.render(rbelLogger.getMessages());
    Files.write(new File("target/websocket.html").toPath(), html.getBytes());
    System.out.println(rbelLogger.getMessagesByOrder().get(12).printTreeStructure());
    assertThat(html)
        .isNotBlank()
        .contains("SUBSCRIBE\\ndestination:/topic/data\\nid:1")
        .contains("Flags: ");
  }

  @SneakyThrows
  @Test
  void checkWebsocketHandshakeMessages() {
    var handshakeMessages =
        rbelLogger.getMessagesByOrder().stream()
            .filter(el -> el.hasFacet(RbelWebsocketHandshakeFacet.class))
            .toList();
    assertThat(handshakeMessages).hasSizeGreaterThanOrEqualTo(2);
    assertThat(handshakeMessages.get(0))
        .hasFacet(RbelWebsocketHandshakeFacet.class)
        .hasStringContentEqualToAtPosition("$.header.[~'connection']", "upgrade")
        .hasStringContentEqualToAtPosition("$.header.[~'upgrade']", "websocket");
    assertThat(handshakeMessages.get(1))
        .hasFacet(RbelWebsocketHandshakeFacet.class)
        .hasStringContentEqualToAtPosition("$.responseCode", "101")
        .hasStringContentEqualToAtPosition("$.header.[~'connection']", "upgrade")
        .hasStringContentEqualToAtPosition("$.header.[~'upgrade']", "websocket");
  }

  @SneakyThrows
  @Test
  void checkWebsocketTrees() {
    assertThat(rbelLogger.getMessagesByOrder().get(6))
        .hasFacet(RbelWebsocketMessageFacet.class)
        .hasGivenValueAtPosition("$.opcode", 1)
        .hasGivenValueAtPosition("$.payloadLength", 1)
        .hasGivenValueAtPosition("$.masked", false)
        .hasGivenValueAtPosition("$.frameType", DATA_FRAME)
        .hasStringContentEqualToAtPosition("$.payload", "o");
    assertThat(rbelLogger.getMessagesByOrder().get(7))
        .hasFacet(RbelWebsocketMessageFacet.class)
        .hasGivenValueAtPosition("$.opcode", 1)
        .hasGivenValueAtPosition("$.payloadLength", 61)
        .hasGivenValueAtPosition("$.masked", true)
        .hasGivenValueAtPosition("$.frameType", DATA_FRAME)
        .extractChildWithPath("$.payload")
        .asString()
        .startsWith("[\"CONNECT\\nheart-beat:");
    assertThat(rbelLogger.getMessagesByOrder().get(13))
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
    var compressionTestLogger =
        RbelLogger.build(
            new RbelConfiguration()
                .setActivateRbelParsingFor(List.of("websocket"))
                .addCapturer(
                    RbelFileReaderCapturer.builder()
                        .rbelFile("src/test/resources/testhub.tgr")
                        .build()));
    try (final var capturer = compressionTestLogger.getRbelCapturer()) {
      capturer.initialize();
    }

    final String html = RbelHtmlRenderer.render(compressionTestLogger.getMessages());
    Files.write(new File("target/wsCompression.html").toPath(), html.getBytes());

    var standardScenarioMessage = compressionTestLogger.getMessagesByOrder().get(17);
    var tokenMessage = compressionTestLogger.getMessagesByOrder().get(18);
    var closeMessage = compressionTestLogger.getMessagesByOrder().get(19);

    assertThat(standardScenarioMessage)
        .hasFacet(RbelResponseFacet.class)
        .hasGivenValueAtPosition("$.opcode", 1)
        .hasGivenValueAtPosition("$.frameType", DATA_FRAME);
    assertThat(standardScenarioMessage.findElement("$.payload").orElseThrow().getRawStringContent())
        .contains("\"type\":\"StandardScenario\"");

    assertThat(tokenMessage)
        .hasFacet(RbelResponseFacet.class)
        .hasGivenValueAtPosition("$.opcode", 1)
        .hasGivenValueAtPosition("$.frameType", DATA_FRAME);
    assertThat(tokenMessage.findElement("$.payload").orElseThrow().getRawStringContent())
        .contains("\"type\":\"Token\"");

    assertThat(closeMessage)
        .hasFacet(RbelResponseFacet.class)
        .hasGivenValueAtPosition("$.opcode", 8)
        .hasGivenValueAtPosition("$.frameType", CLOSE_FRAME);
  }

  @SneakyThrows
  @Test
  void shouldRecognizeWebsocketUpgradesAndHandshakesFromRealTraffic() {
    var trafficTestLogger =
        RbelLogger.build(
            new RbelConfiguration()
                .setActivateRbelParsingFor(List.of("websocket"))
                .addCapturer(
                    RbelFileReaderCapturer.builder()
                        .rbelFile("src/test/resources/websocket-messages.tgr")
                        .build()));
    try (final var capturer = trafficTestLogger.getRbelCapturer()) {
      capturer.initialize();
    }

    final var messages = trafficTestLogger.getMessagesByOrder();
    assertThat(messages).isNotEmpty();
    log.warn("TEST: Total messages retrieved: {}", messages.size());

    // Check for WebSocket handshake messages (HTTP 101 Upgrade responses and requests)
    var handshakeMessages =
        messages.stream().filter(el -> el.hasFacet(RbelWebsocketHandshakeFacet.class)).toList();
    log.warn("TEST: Handshake messages found: {}", handshakeMessages.size());

    // Check for WebSocket frames that follow the handshake
    var websocketMessages =
        messages.stream().filter(el -> el.hasFacet(RbelWebsocketMessageFacet.class)).toList();
    log.warn("TEST: WebSocket messages found: {}", websocketMessages.size());

    // Debug: Check all message facets
    for (int i = 0; i < Math.min(messages.size(), 20); i++) {
      var msg = messages.get(i);
      log.warn(
          "TEST: Message {} {} hasWebsocketHandshake={} hasWebsocketMessage={} hasTcpIp={} hasHttp={}",
          i,
          msg.getUuid(),
          msg.hasFacet(RbelWebsocketHandshakeFacet.class),
          msg.hasFacet(RbelWebsocketMessageFacet.class),
          msg.hasFacet(RbelTcpIpMessageFacet.class),
          msg.hasFacet(RbelHttpMessageFacet.class));
      if (msg.hasFacet(RbelTcpIpMessageFacet.class)
          && !msg.hasFacet(RbelWebsocketHandshakeFacet.class)
          && !msg.hasFacet(RbelWebsocketMessageFacet.class)) {
        log.warn(
            "TEST: TCP-only msg {} conn={} firstBytes={} hasHttp={}",
            msg.getUuid(),
            describeConnection(msg),
            hexPrefix(msg, 6),
            msg.hasFacet(RbelHttpMessageFacet.class));
      }
    }

    log.warn(
        "TEST: Handshake connections: {}",
        handshakeMessages.stream()
            .map(WebsocketConverterTest::describeConnection)
            .distinct()
            .toList());
    log.warn(
        "TEST: WebSocket message connections: {}",
        websocketMessages.stream()
            .map(WebsocketConverterTest::describeConnection)
            .distinct()
            .toList());
    log.warn(
        "TEST: TCP-only connections: {}",
        messages.stream()
            .filter(m -> m.hasFacet(RbelTcpIpMessageFacet.class))
            .filter(m -> !m.hasFacet(RbelHttpMessageFacet.class))
            .filter(m -> !m.hasFacet(RbelWebsocketHandshakeFacet.class))
            .filter(m -> !m.hasFacet(RbelWebsocketMessageFacet.class))
            .map(WebsocketConverterTest::describeConnection)
            .distinct()
            .toList());

    // Verify that we found at least one WebSocket handshake
    assertThat(handshakeMessages)
        .as(
            "Should recognize WebSocket upgrade handshakes - "
                + "this test FAILS with broken handshake detection due to stream consumption bug")
        .hasSizeGreaterThanOrEqualTo(2);

    // Verify that we found WebSocket frames after the handshake
    assertThat(websocketMessages)
        .as("Should recognize WebSocket frames after handshake is established")
        .hasSizeGreaterThanOrEqualTo(1);

    log.info("found {} websocket frames", websocketMessages.size());

    // Verify the first handshake has the required upgrade headers
    var firstHandshake = handshakeMessages.get(0);
    assertThat(firstHandshake)
        .as("WebSocket handshake should have Upgrade: websocket header")
        .hasFacet(RbelWebsocketHandshakeFacet.class)
        .hasStringContentEqualToAtPosition("$.header.[~'upgrade']", "websocket")
        .as("WebSocket handshake should have Connection: upgrade header")
        .extractChildWithPath("$.header.[~'connection']")
        .asString()
        .containsIgnoringCase("upgrade");

    // Verify we have both request and response handshakes
    assertThat(handshakeMessages)
        .as("Should have both WebSocket upgrade request and response handshakes")
        .hasSizeGreaterThanOrEqualTo(2);
  }

  @Test
  void shouldClassifyCrossHopResponseFrameUsingLogicalHandshakeServer() {
    var logger =
        RbelLogger.build(new RbelConfiguration().setActivateRbelParsingFor(List.of("websocket")));
    var converter = logger.getRbelConverter();
    var client = RbelSocketAddress.create("client.example", 12345);
    var logicalServer = RbelSocketAddress.create("logical.example", 443);
    var nextHopProxy = RbelSocketAddress.create("proxy.internal", 9090);

    var handshakeRequest =
        converter.parseMessage(
            websocketHandshakeRequest(),
            new RbelMessageMetadata().withSender(client).withReceiver(logicalServer));
    var handshakeResponse =
        converter.parseMessage(
            websocketHandshakeResponse(),
            new RbelMessageMetadata().withSender(logicalServer).withReceiver(client));
    var frame =
        converter.parseMessage(
            new byte[] {(byte) 0x81, 0x02, 'o', 'k'},
            new RbelMessageMetadata().withSender(nextHopProxy).withReceiver(client));

    assertThat(handshakeRequest).hasFacet(RbelWebsocketHandshakeFacet.class);
    assertThat(handshakeResponse).hasFacet(RbelWebsocketHandshakeFacet.class);
    assertThat(frame).hasFacet(RbelWebsocketMessageFacet.class).hasFacet(RbelResponseFacet.class);
    assertThat(frame.findElement("$.payload").orElseThrow().getRawStringContent()).isEqualTo("ok");
    assertThat(frame.getFacetOrFail(RbelTcpIpMessageFacet.class).getSenderHostname().orElseThrow())
        .isEqualTo(logicalServer);
    assertThat(
            frame.getFacetOrFail(RbelTcpIpMessageFacet.class).getReceiverHostname().orElseThrow())
        .isEqualTo(client);
  }

  private static String describeConnection(RbelElement message) {
    return message
        .getFacet(RbelTcpIpMessageFacet.class)
        .map(facet -> String.valueOf(facet.getTcpIpConnectionIdentifier()))
        .orElse("none");
  }

  private static String hexPrefix(RbelElement message, int maxBytes) {
    byte[] bytes = message.getContent().toByteArray();
    int len = Math.min(maxBytes, bytes.length);
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < len; i++) {
      if (i > 0) {
        builder.append(' ');
      }
      builder.append(String.format("%02X", bytes[i]));
    }
    return builder.toString();
  }

  private static byte[] websocketHandshakeRequest() {
    return ("""
            GET /ws HTTP/1.1\r
            Host: logical.example:443\r
            Upgrade: websocket\r
            Connection: Upgrade\r
            Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r
            Sec-WebSocket-Version: 13\r
            \r
            """)
        .getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] websocketHandshakeResponse() {
    return ("""
            HTTP/1.1 101 Switching Protocols\r
            Connection: upgrade\r
            Upgrade: websocket\r
            Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r
            \r
            """)
        .getBytes(StandardCharsets.UTF_8);
  }
}
