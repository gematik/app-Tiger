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
 *
 */

package de.gematik.test.tiger.proxy.handler;

import static de.gematik.rbellogger.data.RbelMessageMetadata.MESSAGE_TRANSMISSION_TIME;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.util.RbelContent;
import de.gematik.test.tiger.proxy.AbstractTigerProxy;
import de.gematik.test.tiger.proxy.data.TcpConnectionEntry;
import de.gematik.test.tiger.proxy.data.TcpIpConnectionIdentifier;
import java.net.SocketAddress;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/** Buffers incomplete messages and tries to convert them to RbelElements if they are parsable */
@Slf4j
public class MultipleBinaryConnectionParser {
  private final Map<TcpIpConnectionIdentifier, SingleConnectionParser> connectionParsers =
      new ConcurrentHashMap<>();
  private final AbstractTigerProxy tigerProxy;
  private final BinaryExchangeHandler binaryExchangeHandler;

  public MultipleBinaryConnectionParser(
      AbstractTigerProxy tigerProxy, BinaryExchangeHandler binaryExchangeHandler) {
    this.tigerProxy = tigerProxy;
    this.binaryExchangeHandler = binaryExchangeHandler;
  }

  public void addToBuffer(
      SocketAddress senderAddress,
      SocketAddress receiverAddress,
      byte[] part,
      ZonedDateTime timestamp) {
    addToBuffer(
        UUID.randomUUID().toString(),
        senderAddress,
        receiverAddress,
        part,
        Map.of(MESSAGE_TRANSMISSION_TIME.getKey(), timestamp),
        null,
        null);
  }

  public void addToBuffer(
      String uuid,
      SocketAddress senderAddress,
      SocketAddress receiverAddress,
      byte[] part,
      Map<String, Object> additionalData,
      Consumer<RbelElement> messagePreProcessor,
      String previousMessageUuid) {
    val direction = new TcpIpConnectionIdentifier(senderAddress, receiverAddress);
    val connectionParser =
        connectionParsers.computeIfAbsent(
            direction, k -> new SingleConnectionParser(k, tigerProxy, binaryExchangeHandler));
    connectionParser.bufferNewPart(
        TcpConnectionEntry.builder()
            .uuid(uuid)
            .data(RbelContent.of(part))
            .connectionIdentifier(direction)
            .messagePreProcessor(messagePreProcessor)
            .previousUuid(previousMessageUuid)
            .build()
            .addAdditionalData(additionalData));
  }
}
