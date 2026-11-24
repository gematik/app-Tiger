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
package de.gematik.test.tiger.proxy.handler;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageKind;
import de.gematik.rbellogger.util.RbelContent;
import de.gematik.rbellogger.util.RbelSocketAddress;
import de.gematik.test.tiger.proxy.TigerProxy;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SingleConnectionParserBurstTest {

  private static final RbelSocketAddress CLIENT =
      RbelSocketAddress.fromString("client:80").orElseThrow();
  private static final RbelSocketAddress SERVER =
      RbelSocketAddress.fromString("server:80").orElseThrow();

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private MultipleBinaryConnectionParser parser;

  @AfterEach
  void tearDown() {
    if (parser != null) {
      parser.close();
    }
    executor.shutdownNow();
  }

  @Test
  @DisplayName("What a nested converter cuts from a body does not shrink the buffered fragments")
  @SneakyThrows
  void aBurstWithAnInnerFrameInABodyIsParsedCompletely() {
    var body = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nab\r\n";
    var head = "HTTP/1.1 200 OK\r\nContent-Length: " + body.length() + "\r\n\r\n";
    var second = "HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n";
    var third = "HTTP/1.1 202 Accepted\r\nContent-Length: 0\r\n\r\n";
    parser = newParser();

    var parserHeld = new CountDownLatch(1);
    executor.submit(
        () -> {
          parserHeld.await();
          return null;
        });
    var burst = List.of(feed(head), feed(body), feed(second));
    parserHeld.countDown();
    var parsed = new ArrayList<RbelElement>();
    for (var future : burst) {
      parsed.addAll(future.get(10, TimeUnit.SECONDS));
    }
    parsed.addAll(feed(third).get(10, TimeUnit.SECONDS));

    assertThat(parsed.stream().map(message -> new String(message.getRawContent(), UTF_8)))
        .containsExactly(head + body, second, third);
  }

  private MultipleBinaryConnectionParser newParser() {
    var handler = mock(BinaryExchangeHandler.class);
    when(handler.getTigerProxy()).thenReturn(mock(TigerProxy.class));
    var converter = RbelLogger.build().getRbelConverter();
    return new MultipleBinaryConnectionParser(
        conId -> new SingleConnectionParser(conId, executor, converter, handler),
        Duration.ofSeconds(30));
  }

  private CompletableFuture<List<RbelElement>> feed(String content) {
    return parser.addToBuffer(
        SERVER,
        CLIENT,
        RbelContent.of(content, UTF_8),
        ZonedDateTime.now(),
        RbelMessageKind.RESPONSE);
  }
}
