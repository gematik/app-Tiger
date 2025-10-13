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
import de.gematik.test.tiger.proxy.AbstractTigerProxy;
import de.gematik.test.tiger.proxy.data.TcpConnectionEntry;
import de.gematik.test.tiger.proxy.data.TcpIpConnectionIdentifier;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/** Buffers incomplete messages and tries to convert them to RbelElements if they are parsable */
@Slf4j
@AllArgsConstructor
public class MultipleBinaryConnectionParser {
  private final Map<TcpIpConnectionIdentifier, SingleConnectionParser> connectionParsers =
      new ConcurrentHashMap<>();
  private final Function<TcpIpConnectionIdentifier, SingleConnectionParser>
      createSingleConnectionParser;
  private final List<CompletableFuture<List<RbelElement>>> currentParsingTasks =
      Collections.synchronizedList(new ArrayList<>());

  public MultipleBinaryConnectionParser(
      AbstractTigerProxy tigerProxy, BinaryExchangeHandler binaryExchangeHandler) {
    this.createSingleConnectionParser =
        conId -> new SingleConnectionParser(conId, tigerProxy, binaryExchangeHandler);
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
    synchronized (currentParsingTasks) {
      currentParsingTasks.add(future);
    }
    future.whenComplete(
        (message, throwable) -> {
          synchronized (currentParsingTasks) {
            currentParsingTasks.remove(future);
          }
        });
    return future;
  }

  public void waitForAllParsingTasksToBeFinished() {
    List<CompletableFuture<?>> tasks;
    synchronized (currentParsingTasks) {
      tasks = new ArrayList<>(currentParsingTasks);
    }
    if (!tasks.isEmpty()) {
      log.trace("Waiting for all parsing tasks to finish, found {} tasks", tasks.size());
      val currentParsingTasksFuture =
          CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]));
      currentParsingTasksFuture.join();
      log.trace("All {} parsing tasks finished", tasks.size());
    }
  }
}
