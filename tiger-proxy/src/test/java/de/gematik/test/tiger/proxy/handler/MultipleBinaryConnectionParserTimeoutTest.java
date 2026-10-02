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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelMessageKind;
import de.gematik.rbellogger.util.RbelSocketAddress;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.util.TcpIpConnectionIdentifier;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyParsingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MultipleBinaryConnectionParserTimeoutTest {

  private static final Duration SHORT_TIMEOUT = Duration.ofMillis(300);
  private static final RbelSocketAddress CLIENT =
      RbelSocketAddress.fromString("client:80").orElseThrow();
  private static final RbelSocketAddress SERVER =
      RbelSocketAddress.fromString("server:80").orElseThrow();

  private final ExecutorService executor = Executors.newCachedThreadPool();
  private final ExecutorService jammed = Executors.newSingleThreadExecutor();
  private final CountDownLatch release = new CountDownLatch(1);

  @AfterEach
  void tearDown() {
    release.countDown();
    jammed.shutdownNow();
    executor.shutdownNow();
  }

  @Test
  @DisplayName("A parsing task that never finishes does not block the caller for ever")
  void aStuckTaskDoesNotBlockForEver() {
    var parser = parserThatNeverFinishes();
    parser.addToBuffer(CLIENT, SERVER, request(), ZonedDateTime.now(), RbelMessageKind.REQUEST);

    assertTimeoutPreemptively(
        Duration.ofSeconds(10),
        () ->
            assertThatThrownBy(() -> parser.waitForAllParsingTasksToBeFinished())
                .as("without a timeout the caller hangs on whatever the parser is stuck on")
                .isInstanceOf(TigerProxyParsingException.class)
                .hasMessageContaining("Gave up"));
  }

  @Test
  @DisplayName("The message that is still parsing is named")
  void theStuckMessageIsNamed() {
    var parser = parserThatNeverFinishes();
    parser.addToBuffer(
        "the-stuck-one", CLIENT, SERVER, request(), Map.of(), RbelMessageKind.REQUEST, null, null);

    assertTimeoutPreemptively(
        Duration.ofSeconds(10),
        () ->
            assertThatThrownBy(() -> parser.waitForAllParsingTasksToBeFinished())
                .as("a caller that gave up needs to know which message it was waiting on")
                .hasMessageContaining("the-stuck-one"));
  }

  @Test
  @DisplayName("A message that was given up on is not waited for a second time")
  void anAbandonedTaskIsNotWaitedForAgain() {
    var parser = parserThatNeverFinishes();
    parser.addToBuffer(CLIENT, SERVER, request(), ZonedDateTime.now(), RbelMessageKind.REQUEST);

    assertThatThrownBy(() -> parser.waitForAllParsingTasksToBeFinished())
        .isInstanceOf(TigerProxyParsingException.class);

    assertThatCode(() -> parser.waitForAllParsingTasksToBeFinished())
        .as("the task is still stuck, so a second wait would pay the timeout all over again")
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("The timeout configured on the proxy is the one the parser uses")
  void theConfiguredTimeoutIsUsed() {
    var parser = parserOfProxyConfiguredWith(1);
    parser.addToBuffer(CLIENT, SERVER, request(), ZonedDateTime.now(), RbelMessageKind.REQUEST);

    assertTimeoutPreemptively(
        Duration.ofSeconds(30),
        () ->
            assertThatThrownBy(() -> parser.waitForAllParsingTasksToBeFinished())
                .as("tigerProxy.parsingTimeoutInSeconds has to reach the waiting code")
                .isInstanceOf(TigerProxyParsingException.class)
                .hasMessageContaining("Gave up after 1 seconds"));
  }

  @Test
  @DisplayName("Messages that parse normally are still waited for")
  void finishedTasksAreStillWaitedFor() {
    var parser = parserThatFinishes();
    parser.addToBuffer(CLIENT, SERVER, request(), ZonedDateTime.now(), RbelMessageKind.REQUEST);

    assertThatCode(() -> parser.waitForAllParsingTasksToBeFinished()).doesNotThrowAnyException();
  }

  private MultipleBinaryConnectionParser parserThatNeverFinishes() {
    jamTheOnlyThread();
    return new MultipleBinaryConnectionParser(
        conId -> connectionParserOn(conId, jammed), SHORT_TIMEOUT);
  }

  private MultipleBinaryConnectionParser parserOfProxyConfiguredWith(int timeoutInSeconds) {
    jamTheOnlyThread();
    var tigerProxy = mock(TigerProxy.class);
    when(tigerProxy.getTigerProxyConfiguration())
        .thenReturn(
            TigerProxyConfiguration.builder().parsingTimeoutInSeconds(timeoutInSeconds).build());
    when(tigerProxy.getRbelLogger()).thenReturn(RbelLogger.build());
    when(tigerProxy.getExecutor()).thenReturn(jammed);
    return new MultipleBinaryConnectionParser(tigerProxy, mock(BinaryExchangeHandler.class));
  }

  private MultipleBinaryConnectionParser parserThatFinishes() {
    return new MultipleBinaryConnectionParser(
        conId -> connectionParserOn(conId, executor), Duration.ofSeconds(30));
  }

  @SneakyThrows
  private void jamTheOnlyThread() {
    var occupied = new CountDownLatch(1);
    jammed.submit(
        () -> {
          occupied.countDown();
          release.await();
          return null;
        });
    occupied.await();
  }

  private SingleConnectionParser connectionParserOn(
      TcpIpConnectionIdentifier connectionId, ExecutorService on) {
    var handler = mock(BinaryExchangeHandler.class);
    when(handler.getTigerProxy()).thenReturn(mock(TigerProxy.class));
    return new SingleConnectionParser(
        connectionId, on, RbelLogger.build().getRbelConverter(), handler);
  }

  private static byte[] request() {
    return "GET / HTTP/1.1\r\nHost: server\r\nContent-Length: 0\r\n\r\n"
        .getBytes(StandardCharsets.UTF_8);
  }
}
