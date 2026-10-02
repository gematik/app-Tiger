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

import static de.gematik.rbellogger.data.RbelMessageMetadata.MESSAGE_TRANSMISSION_TIME;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageKind;
import de.gematik.rbellogger.util.RbelContent;
import de.gematik.rbellogger.util.RbelSocketAddress;
import de.gematik.test.tiger.common.util.TcpIpConnectionIdentifier;
import de.gematik.test.tiger.proxy.AbstractTigerProxy;
import de.gematik.test.tiger.proxy.data.TcpConnectionEntry;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyParsingException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/** Buffers incomplete messages and tries to convert them to RbelElements if they are parsable */
@Slf4j
public class MultipleBinaryConnectionParser {
  private final Map<TcpIpConnectionIdentifier, SingleConnectionParser> connectionParsers =
      new ConcurrentHashMap<>();
  private final Function<TcpIpConnectionIdentifier, SingleConnectionParser>
      createSingleConnectionParser;
  private final List<ParsingTask> currentParsingTasks =
      Collections.synchronizedList(new ArrayList<>());

  private final Duration parsingTimeout;
  private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(5);
  private final AtomicReference<Instant> lastActivity = new AtomicReference<>(Instant.now());
  private final AtomicBoolean sweepArmed = new AtomicBoolean();
  private final AtomicBoolean closed = new AtomicBoolean();

  private static final ScheduledExecutorService SWEEP_SCHEDULER =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            val thread = Executors.defaultThreadFactory().newThread(runnable);
            thread.setName("TigerProxy-connection-sweeper");
            thread.setDaemon(true);
            return thread;
          });

  public MultipleBinaryConnectionParser(
      Function<TcpIpConnectionIdentifier, SingleConnectionParser> createSingleConnectionParser,
      Duration parsingTimeout) {
    this.createSingleConnectionParser = createSingleConnectionParser;
    this.parsingTimeout = parsingTimeout;
  }

  public MultipleBinaryConnectionParser(
      AbstractTigerProxy tigerProxy, BinaryExchangeHandler binaryExchangeHandler) {
    this.createSingleConnectionParser =
        conId -> new SingleConnectionParser(conId, tigerProxy, binaryExchangeHandler);
    this.parsingTimeout =
        Duration.ofSeconds(tigerProxy.getTigerProxyConfiguration().getParsingTimeoutInSeconds());
    registerMessageRemovalCallbacks(tigerProxy);
  }

  private void registerMessageRemovalCallbacks(AbstractTigerProxy tigerProxy) {
    val converter = tigerProxy.getRbelLogger().getRbelConverter();
    converter.addClearHistoryCallback(
        () -> connectionParsers.values().forEach(SingleConnectionParser::forgetLastMessage));
    converter.addMessageRemovedFromHistoryCallback(
        element ->
            connectionParsers
                .values()
                .forEach(parser -> parser.forgetLastMessageIfItIs(element.getUuid())));
    tigerProxy.addRemovedMessageUuidsHandler(
        uuids ->
            connectionParsers.values().forEach(parser -> parser.forgetLastMessageIfAmong(uuids)));
  }

  public CompletableFuture<List<RbelElement>> addToBuffer(
      RbelSocketAddress senderAddress,
      RbelSocketAddress receiverAddress,
      byte[] part,
      ZonedDateTime timestamp,
      RbelMessageKind messageKind) {
    return addToBuffer(
        UUID.randomUUID().toString(),
        senderAddress,
        receiverAddress,
        part,
        Map.of(MESSAGE_TRANSMISSION_TIME.getKey(), timestamp),
        messageKind,
        null,
        null);
  }

  public CompletableFuture<List<RbelElement>> addToBuffer(
      String uuid,
      RbelSocketAddress senderAddress,
      RbelSocketAddress receiverAddress,
      byte[] part,
      Map<String, Object> additionalData,
      RbelMessageKind messageKind,
      Consumer<RbelElement> messagePreProcessor,
      String previousMessageUuid) {
    lastActivity.set(Instant.now());
    armIdleSweep();
    val connectionId = new TcpIpConnectionIdentifier(senderAddress, receiverAddress);
    val connectionParser =
        connectionParsers.computeIfAbsent(connectionId, createSingleConnectionParser);
    val future =
        connectionParser.bufferNewPart(
            TcpConnectionEntry.builder()
                .uuid(uuid)
                .data(RbelContent.of(part))
                .connectionIdentifier(connectionId)
                .messagePreProcessor(messagePreProcessor)
                .previousUuid(previousMessageUuid)
                .messageKind(messageKind)
                .build()
                .addAdditionalData(additionalData));
    val task = new ParsingTask(uuid, future);
    synchronized (currentParsingTasks) {
      currentParsingTasks.add(task);
    }
    future.whenComplete(
        (message, throwable) -> {
          synchronized (currentParsingTasks) {
            currentParsingTasks.remove(task);
          }
        });
    return future;
  }

  private void armIdleSweep() {
    if (!closed.get() && sweepArmed.compareAndSet(false, true)) {
      scheduleSweep(IDLE_TIMEOUT);
    }
  }

  private void sweepOrRearm() {
    if (closed.get()) {
      sweepArmed.set(false);
      return;
    }
    val idleFor = Duration.between(lastActivity.get(), Instant.now());
    if (idleFor.compareTo(IDLE_TIMEOUT) < 0) {
      scheduleSweep(IDLE_TIMEOUT.minus(idleFor));
      return;
    }
    sweepArmed.set(false);
    evictIdleConnections(Instant.now());
  }

  private void scheduleSweep(Duration delay) {
    SWEEP_SCHEDULER.schedule(this::sweepOrRearm, delay.toMillis(), TimeUnit.MILLISECONDS);
  }

  void evictIdleConnections(Instant now) {
    val threshold = now.minus(IDLE_TIMEOUT);
    connectionParsers.values().removeIf(parser -> parser.isDrainedAndIdleSince(threshold));
  }

  public void close() {
    closed.set(true);
    connectionParsers.clear();
  }

  int openConnectionCount() {
    return connectionParsers.size();
  }

  public void waitForAllParsingTasksToBeFinished() {
    List<ParsingTask> tasks;
    synchronized (currentParsingTasks) {
      tasks = new ArrayList<>(currentParsingTasks);
    }
    if (!tasks.isEmpty()) {
      log.trace("Waiting for all parsing tasks to finish, found {} tasks", tasks.size());
      awaitAll(tasks);
      log.trace("All {} parsing tasks finished", tasks.size());
    }
    evictIdleConnections(Instant.now());
  }

  private void awaitAll(List<ParsingTask> tasks) {
    try {
      CompletableFuture.allOf(
              tasks.stream().map(ParsingTask::future).toArray(CompletableFuture[]::new))
          .get(parsingTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      val abandoned = stillRunning(tasks);
      synchronized (currentParsingTasks) {
        currentParsingTasks.removeAll(abandoned);
      }
      throw new TigerProxyParsingException(
          "Gave up after %d seconds waiting for %s to finish parsing"
              .formatted(parsingTimeout.toSeconds(), describe(abandoned)),
          e);
    } catch (ExecutionException e) {
      throw new TigerProxyParsingException("Error while parsing buffered messages", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TigerProxyParsingException("Interrupted while parsing buffered messages", e);
    }
  }

  private static List<ParsingTask> stillRunning(List<ParsingTask> tasks) {
    return tasks.stream().filter(task -> !task.future().isDone()).toList();
  }

  private static String describe(List<ParsingTask> tasks) {
    return tasks.stream()
        .map(ParsingTask::uuid)
        .collect(Collectors.joining(", ", "message(s) ", ""));
  }

  private record ParsingTask(String uuid, CompletableFuture<List<RbelElement>> future) {}
}
