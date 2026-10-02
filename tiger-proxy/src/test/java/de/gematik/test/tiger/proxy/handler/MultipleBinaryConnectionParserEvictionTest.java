/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package de.gematik.test.tiger.proxy.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelMessageKind;
import de.gematik.rbellogger.util.RbelSocketAddress;
import de.gematik.test.tiger.proxy.TigerProxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MultipleBinaryConnectionParserEvictionTest {

  private static final RbelSocketAddress CLIENT =
      RbelSocketAddress.fromString("client:80").orElseThrow();
  private static final RbelSocketAddress SERVER =
      RbelSocketAddress.fromString("server:80").orElseThrow();

  private final ExecutorService executor = Executors.newCachedThreadPool();
  private MultipleBinaryConnectionParser parser;

  @AfterEach
  void tearDown() {
    if (parser != null) {
      parser.close();
    }
    executor.shutdownNow();
  }

  @Test
  @DisplayName("A connection that fell quiet and holds no bytes is dropped")
  void quietDrainedConnectionIsDropped() {
    parser = newParser();
    feed(parser, completeRequest());
    assertThat(parser.openConnectionCount()).isEqualTo(1);

    parser.evictIdleConnections(Instant.now().plus(Duration.ofHours(1)));

    assertThat(parser.openConnectionCount())
        .as("nothing ever removed these, so a proxy kept one buffer per connection it had seen")
        .isZero();
  }

  @Test
  @DisplayName("A connection still holding a partial message is kept")
  void connectionHoldingAPartialMessageIsKept() {
    parser = newParser();
    feed(parser, partialRequest());

    parser.evictIdleConnections(Instant.now().plus(Duration.ofHours(1)));

    assertThat(parser.openConnectionCount())
        .as("those bytes are the start of a message that has not arrived in full yet")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("A connection that is still busy is kept")
  void busyConnectionIsKept() {
    parser = newParser();
    feed(parser, completeRequest());

    parser.evictIdleConnections(Instant.now());

    assertThat(parser.openConnectionCount()).isEqualTo(1);
  }

  private MultipleBinaryConnectionParser newParser() {
    var handler = mock(BinaryExchangeHandler.class);
    when(handler.getTigerProxy()).thenReturn(mock(TigerProxy.class));
    var converter = RbelLogger.build().getRbelConverter();
    return new MultipleBinaryConnectionParser(
        conId -> new SingleConnectionParser(conId, executor, converter, handler),
        Duration.ofSeconds(30));
  }

  @SneakyThrows
  private void feed(MultipleBinaryConnectionParser parser, byte[] content) {
    parser.addToBuffer(CLIENT, SERVER, content, ZonedDateTime.now(), RbelMessageKind.REQUEST).get();
  }

  private static byte[] completeRequest() {
    return "GET /done HTTP/1.1\r\nHost: server\r\nContent-Length: 0\r\n\r\n"
        .getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] partialRequest() {
    return "GET /truncated HTTP/1.1\r\nHost: server\r\nContent-Length: 40\r\n\r\ntoo short"
        .getBytes(StandardCharsets.UTF_8);
  }
}
