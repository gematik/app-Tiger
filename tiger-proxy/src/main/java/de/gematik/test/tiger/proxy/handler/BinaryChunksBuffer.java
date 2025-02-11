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

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelElementConvertionPair;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelRequestFacet;
import de.gematik.rbellogger.data.facet.RbelResponseFacet;
import de.gematik.rbellogger.data.facet.RbelRootFacet;
import de.gematik.rbellogger.data.facet.UnparsedChunkFacet;
import de.gematik.rbellogger.util.RbelContent;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.data.SenderReceiverPair;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/** Buffers incomplete messages and tries to convert them to RbelElements if they are parsable */
@Slf4j
public class BinaryChunksBuffer {
  private final BundledServerNamesAdder bundledServerNamesAdder = new BundledServerNamesAdder();
  private final Map<SenderReceiverPair, RbelContent> bufferedParts = new ConcurrentHashMap<>();
  private final RbelConverter rbelConverter;
  private final TigerProxyConfiguration proxyConfiguration;

  public BinaryChunksBuffer(
      RbelConverter rbelConverter, TigerProxyConfiguration proxyConfiguration) {
    this.rbelConverter = rbelConverter;
    this.proxyConfiguration = proxyConfiguration;
  }

  private void removePart(SenderReceiverPair key) {
    bufferedParts.remove(key);
  }

  /** returns the complete message for this key with the given part appended to it */
  private RbelContent addToBuffer(SenderReceiverPair key, RbelContent part) {
    RbelContent bufferedContent =
        bufferedParts.computeIfAbsent(key, k -> RbelContent.builder().build());
    bufferedContent.append(part);
    return bufferedContent;
  }

  public Optional<RbelElement> tryToConvertMessageAndBufferUnusedBytes(
      RbelContent message, SocketAddress senderAddress, SocketAddress receiverAddress) {
    var key = new SenderReceiverPair(senderAddress, receiverAddress);
    final Optional<RbelElement> requestOptional =
        tryToConvertMessage(addToBuffer(key, message), key);
    if (requestOptional.isPresent()) {
      removePart(key);
    }
    return requestOptional;
  }

  private Optional<RbelElement> tryToConvertMessage(
      RbelContent messageContent, SenderReceiverPair connectionKey) {
    var messageElement = new RbelElement(messageContent);
    final RbelElement result =
        rbelConverter.parseMessage(
            new RbelElementConvertionPair(messageElement),
            toRbelHostname(connectionKey.sender()),
            toRbelHostname(connectionKey.receiver()),
            Optional.empty(),
            RbelConverter.FinishProcessing.NO);
    if (proxyConfiguration.isActivateRbelParsing()
        && result.getFacets().stream()
            .noneMatch(
                f ->
                    f instanceof RbelRootFacet
                        || f instanceof RbelResponseFacet
                        || f instanceof RbelRequestFacet)) {
      rbelConverter.removeMessage(result);
      RbelConverter.setMessageFullyProcessed(result);
      return Optional.empty();
    }

    bundledServerNamesAdder.addBundledServerNameToHostnameFacet(result);
    if (!proxyConfiguration.isActivateRbelParsing()) {
      result.addFacet(new UnparsedChunkFacet());
    }
    return Optional.of(result);
  }

  private RbelHostname toRbelHostname(SocketAddress socketAddress) {
    if (socketAddress instanceof InetSocketAddress inetSocketAddress) {
      return RbelHostname.builder()
          .hostname(inetSocketAddress.getHostName())
          .port(inetSocketAddress.getPort())
          .build();
    } else {
      log.warn(
          "Incompatible socketAddress encountered: " + socketAddress.getClass().getSimpleName());
      return null;
    }
  }
}
