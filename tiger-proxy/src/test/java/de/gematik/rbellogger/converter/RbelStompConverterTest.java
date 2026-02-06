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
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.websocket.RbelStompFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RbelStompConverterTest {

  private RbelLogger rbelLogger;

  @BeforeEach
  void setUp() throws Exception {
    final RbelFileReaderCapturer fileReaderCapturer =
        RbelFileReaderCapturer.builder().rbelFile("src/test/resources/stomp.tgr").build();
    rbelLogger =
        RbelLogger.build(
            new RbelConfiguration()
                .addCapturer(fileReaderCapturer)
                .activateConversionFor("websocket"));
    fileReaderCapturer.initialize();
    fileReaderCapturer.close();
  }

  @Test
  void shouldRecognizeStompConnectMessage() {
    final RbelElement stompConnectMessage =
        rbelLogger.getMessages().stream().skip(7).findFirst().get();

    assertThat(stompConnectMessage)
        .hasGivenFacetAtPosition("$.payload.0.content", RbelStompFacet.class)
        .hasStringContentEqualToAtPosition("$.payload.0.content.command", "CONNECT")
        .hasStringContentEqualToAtPosition("$.payload.0.content.headers.heart-beat", "0,0")
        .hasStringContentEqualToAtPosition("$.payload.0.content.headers.accept-version", "1.1,1.2")
        .hasStringContentEqualToAtPosition("$.payload.0.content.body", "");
  }

  @Test
  void shouldRecognizeStompConnectedMessage() {
    final RbelElement stompConnectedMessage =
        rbelLogger.getMessages().stream().skip(8).findFirst().get();

    assertThat(stompConnectedMessage)
        .hasGivenFacetAtPosition("$.payload.content.0.content", RbelStompFacet.class)
        .hasStringContentEqualToAtPosition("$.payload.content.0.content.command", "CONNECTED")
        .hasStringContentEqualToAtPosition("$.payload.content.0.content.headers.version", "1.2")
        .hasStringContentEqualToAtPosition("$.payload.content.0.content.headers.heart-beat", "0,0")
        .hasStringContentEqualToAtPosition("$.payload.content.0.content.body", "");
  }

  @Test
  void shouldRecognizeStompSubscribeMessage() {
    final RbelElement stompSubscribeMessage =
        rbelLogger.getMessages().stream().skip(9).findFirst().get();

    assertThat(stompSubscribeMessage)
        .hasGivenFacetAtPosition("$.payload.0.content", RbelStompFacet.class)
        .hasStringContentEqualToAtPosition("$.payload.0.content.command", "SUBSCRIBE")
        .hasStringContentEqualToAtPosition("$.payload.0.content.headers.id", "0")
        .hasStringContentEqualToAtPosition(
            "$.payload.0.content.headers.destination.basicPath", "/topic/traces")
        .hasStringContentEqualToAtPosition(
            "$.payload.0.content.headers.destination.path", "/topic/traces")
        .hasStringContentEqualToAtPosition("$.payload.0.content.body", "");
  }

  @SneakyThrows
  @Test
  void shouldRenderCleanHtmlIncludingHeaders() {
    final String html = RbelHtmlRenderer.render(rbelLogger.getMessages());

    Assertions.assertThat(html)
        .contains("<h1 class=\"font-monospace title\">STOMP<span>(CONNECT)</span>")
        .contains(
            "<td><pre class=\"key\">destination</pre></td><td><pre"
                + " class=\"value\">/topic/traces</pre></td>")
        .contains(
            "<tr><td><pre class=\"key\">id</pre></td><td><pre class=\"value\">0</pre></td></tr>");
  }
}
