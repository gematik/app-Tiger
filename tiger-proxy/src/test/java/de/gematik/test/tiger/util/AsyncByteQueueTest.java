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
package de.gematik.test.tiger.util;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.util.RbelContent;
import de.gematik.rbellogger.util.RbelSocketAddress;
import de.gematik.test.tiger.common.util.TcpIpConnectionIdentifier;
import de.gematik.test.tiger.proxy.data.TcpConnectionEntry;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Random;
import java.util.concurrent.*;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class AsyncByteQueueTest {

  private static final RbelSocketAddress CLIENT = RbelSocketAddress.create("localhost", 8080);
  private static final RbelSocketAddress SERVER = RbelSocketAddress.create("localhost", 9090);

  private AsyncByteQueue queue;
  private TcpIpConnectionIdentifier pair;

  @BeforeEach
  void setup() {
    pair = new TcpIpConnectionIdentifier(CLIENT, SERVER);
    queue = new AsyncByteQueue(pair);
  }

  private void addClientMessage(String data) {
    addClientMessage(data, null);
  }

  private void addClientMessage(String data, String uuid) {
    queue.write(
        TcpConnectionEntry.builder()
            .uuid(uuid)
            .data(RbelContent.of(data.getBytes(StandardCharsets.UTF_8)))
            .connectionIdentifier(new TcpIpConnectionIdentifier(CLIENT, SERVER))
            .build()
            .addAdditionalData(
                RbelMessageMetadata.MESSAGE_TRANSMISSION_TIME.getKey(), ZonedDateTime.now()));
  }

  private void addServerMessage(String data) {
    addServerMessage(data, null);
  }

  private void addServerMessage(String data, String uuid) {
    queue.write(
        TcpConnectionEntry.builder()
            .uuid(uuid)
            .data(RbelContent.of(data.getBytes(StandardCharsets.UTF_8)))
            .connectionIdentifier(new TcpIpConnectionIdentifier(SERVER, CLIENT))
            .build()
            .addAdditionalData(
                RbelMessageMetadata.MESSAGE_TRANSMISSION_TIME.getKey(), ZonedDateTime.now()));
  }

  private String peekQueue() {
    val result = queue.peek();
    if (result.getData().isEmpty()) {
      return null;
    }
    return new String(result.getData().toByteArray(), StandardCharsets.UTF_8);
  }

  private void consumeQueue(int count) {
    queue.consume(count);
  }

  @Test
  void testWriteAndPeek() {
    addClientMessage("Hello");
    addClientMessage(" World");

    assertThat(peekQueue()).isEqualTo("Hello World");
  }

  @Test
  void testConsumePartial() {
    addClientMessage("Hello World");

    consumeQueue("Hello ".length());
    assertThat(peekQueue()).isEqualTo("World");
  }

  @Test
  void testConsumeExact() {
    addClientMessage("Test");

    consumeQueue(4); // Remove all
    assertThat(queue.peek().getData().size()).isZero();
    assertThat(queue.isEmpty()).isTrue();
  }

  @Test
  void testConsumeTwoPackages() {
    addClientMessage("Hello World!");

    consumeQueue("Hello World".length());
    assertThat(peekQueue()).isEqualTo("!");
  }

  @Test
  void testConsumeMoreThanAvailable() {
    addClientMessage("Small");

    consumeQueue(10); // Try to consume more than exists
    assertThat(queue.peek().getData().size()).isZero();
    assertThat(queue.isEmpty()).isTrue();
  }

  @Test
  void testInterleavedWriteAndRead() {
    addClientMessage("ABC");
    assertThat(peekQueue()).isEqualTo("ABC");

    consumeQueue(1); // Remove 'A'
    assertThat(peekQueue()).isEqualTo("BC");

    addClientMessage("DE");
    assertThat(peekQueue()).isEqualTo("BCDE");
  }

  @Test
  void testEmptyQueuePeek() {
    assertThat(queue.peek().getData().size()).isZero();
    assertThat(queue.isEmpty()).isTrue();
  }

  @Test
  void testEmptyQueueConsume() {
    consumeQueue(5); // Should do nothing

    assertThat(queue.peek().getData().size()).isZero();
    assertThat(queue.isEmpty()).isTrue();
  }

  @Test
  void testLargeDataHandling() {
    byte[] largeData = new byte[1000000];
    new Random().nextBytes(largeData);
    queue.write(
        TcpConnectionEntry.builder()
            .data(RbelContent.of(largeData))
            .connectionIdentifier(new TcpIpConnectionIdentifier(CLIENT, SERVER))
            .build()
            .addAdditionalData(
                RbelMessageMetadata.MESSAGE_TRANSMISSION_TIME.getKey(), ZonedDateTime.now()));

    assertThat(queue.peek().getData().size()).isEqualTo(1000000);
    queue.consume(500000);
    assertThat(queue.peek().getData().size()).isEqualTo(500000);
  }

  @Test
  void testConcurrency() throws InterruptedException {
    int threadCount = 10;
    int writesPerThread = 100;
    RbelContent data = RbelContent.of("X".getBytes(StandardCharsets.UTF_8));

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    for (int i = 0; i < threadCount; i++) {
      executor.execute(
          () -> {
            for (int j = 0; j < writesPerThread; j++) {
              addClientMessage(data.toReadableString());
            }
          });
    }

    executor.awaitTermination(5, TimeUnit.SECONDS);
    executor.shutdown();

    assertThat(queue.peek().getData().size())
        .isEqualTo(threadCount * writesPerThread * data.size());
  }

  @Test
  void testAvailableBytesInitiallyEmpty() {
    assertThat(queue.availableBytes()).isEqualTo(0);
  }

  @Test
  void testAvailableBytesAfterWrite() {
    addClientMessage("Hello");
    assertThat(queue.availableBytes()).isEqualTo(5);
  }

  @Test
  void testAvailableBytesAfterPartialConsumption() {
    addClientMessage("HelloWorld");
    consumeQueue(5); // Remove "Hello"
    assertThat(queue.availableBytes()).isEqualTo(5);
  }

  @Test
  void testAvailableBytesAfterMultipleWritesAndConsumes() {
    addClientMessage("12345");
    addClientMessage("67890");

    assertThat(queue.availableBytes()).isEqualTo(10);

    consumeQueue(3); // "123"
    assertThat(queue.availableBytes()).isEqualTo(7);

    consumeQueue(4); // "45 & 67"
    assertThat(queue.availableBytes()).isEqualTo(3);
  }

  @RepeatedTest(50)
  void testAvailableBytesWithConcurrentWrites() throws InterruptedException {
    ExecutorService executor = Executors.newFixedThreadPool(3);

    executor.execute(() -> addClientMessage("ABC"));
    executor.execute(() -> addClientMessage("DEF"));
    executor.execute(() -> addClientMessage("GHI"));

    executor.shutdown();
    executor.awaitTermination(1, TimeUnit.SECONDS);

    assertThat(queue.availableBytes()).isEqualTo(9);
  }

  @Test
  void testDirectionalMixedMessages() {
    addClientMessage("ClientMessage");
    addServerMessage("ServerMessage");

    assertThat(peekQueue()).isEqualTo("ClientMessage");
    consumeQueue("ClientMessage".length());
    assertThat(peekQueue()).isEqualTo("ServerMessage");
  }

  @Test
  void testDirectionalInterleavedMessages() {
    addClientMessage("Client1");
    addServerMessage("Server1");
    addClientMessage("Client2");
    addServerMessage("Server2");

    assertThat(peekQueue()).isEqualTo("Client1Client2");
    consumeQueue("Client1".length());
    assertThat(peekQueue()).isEqualTo("Server1Server2");
    consumeQueue("Server1".length());
    assertThat(peekQueue()).isEqualTo("Client2");
    consumeQueue("Client2".length());
    assertThat(peekQueue()).isEqualTo("Server2");
  }

  @Test
  void testMultipleClientAndMultipleServerMessages() {
    addClientMessage("Client1");
    addClientMessage("Client2");
    addServerMessage("Server1");
    addServerMessage("Server2");
    addClientMessage("Client3");
    addClientMessage("Client4");
    addServerMessage("Server3");

    assertThat(peekQueue()).isEqualTo("Client1Client2Client3Client4");
    consumeQueue("Client1".length());
    assertThat(peekQueue()).isEqualTo("Client2Client3Client4");
    consumeQueue("Client2".length());
    assertThat(peekQueue()).isEqualTo("Server1Server2Server3");
    consumeQueue("Server1".length());
    assertThat(peekQueue()).isEqualTo("Server2Server3");
    consumeQueue("Server2".length());
    assertThat(peekQueue()).isEqualTo("Client3Client4");
    consumeQueue("Client3".length());
    assertThat(peekQueue()).isEqualTo("Client4");
    consumeQueue("Client4".length());
    assertThat(peekQueue()).isEqualTo("Server3");
    consumeQueue("Server3".length());
  }

  @Test
  void testTailUpdateWithInterleavedWritesAndReads() {
    addClientMessage("Message1");
    consumeQueue("Message1".length());
    addClientMessage("Message2");
    consumeQueue("Message2".length());
    addClientMessage("Message3");

    assertThat(peekQueue()).isEqualTo("Message3");
  }

  @Test
  void consumeNextMessageInterleaved() {
    addClientMessage("{Client1");
    assertThat(peekQueue()).isEqualTo("{Client1");
    addServerMessage("{Server1");
    assertThat(peekQueue()).isEqualTo("{Client1");
    addClientMessage("Client2}");
    assertThat(peekQueue()).isEqualTo("{Client1Client2}");
    addServerMessage("Server2}");
    assertThat(peekQueue()).isEqualTo("{Client1Client2}");

    consumeQueue("{Client1Client2}".length());
    assertThat(peekQueue()).isEqualTo("{Server1Server2}");
    consumeQueue("{Server1Server2}".length());
  }

  @Test
  void readingInterleavedMessagesWithIntermittentWriting() {
    addClientMessage("{Client1");
    addServerMessage("{Server1");
    addClientMessage("Client2}");
    consumeQueue("{Client1Client2}".length());
    addServerMessage("Server2}");
    assertThat(peekQueue()).isEqualTo("{Server1Server2}");
    consumeQueue("{Server1Server2}".length());
  }

  @Test
  void consumeAroundHangingNode() {
    addClientMessage("c1");
    addServerMessage("s1");
    addClientMessage("c2");
    addServerMessage("s2");

    consumeQueue("c1c2".length());
    assertThat(peekQueue()).isEqualTo("s1s2");
    consumeQueue("s1".length());
    assertThat(peekQueue()).isEqualTo("s2");
  }
}
