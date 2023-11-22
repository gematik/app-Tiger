/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.proxy.client;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.proxy.data.TigerNonPairedMessageFacet;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Data
@Slf4j
public class TracingMessageIsolani implements TracingMessageFrame {

  private PartialTracingMessage message;
  @ToString.Exclude private final TigerRemoteProxyClient remoteProxyClient;

  @Override
  public void checkForCompletePairAndPropagateIfComplete() {
    if (message != null && message.isComplete()) {
      remoteProxyClient.submitNewMessageTask(this::parseAndPropagate);
    }
  }

  private void parseAndPropagate() {
    if (remoteProxyClient.messageUuidKnown(message.getTracingDto().getRequestUuid())) {
      log.trace(
          "{}Skipping parsing of pair with UUIDs ({} and {}) (received from PUSH): UUID already"
              + " known",
          remoteProxyClient.proxyName(),
          message.getTracingDto().getRequestUuid(),
          message.getTracingDto().getResponseUuid());
      return;
    }
    val messageParsed =
        remoteProxyClient.buildNewRbelMessage(
            message.getSender(),
            message.getReceiver(),
            message.buildCompleteContent(),
            Optional.ofNullable(message.getTransmissionTime()),
            message.getTracingDto().getRequestUuid());
    if (messageParsed.isEmpty()) {
      return;
    }

    messageParsed.get().addFacet(new TigerNonPairedMessageFacet());
    if (log.isTraceEnabled()) {
      log.trace(
          "{}Received isolani message to {} (UUID {})",
          remoteProxyClient.proxyName(),
          messageParsed
              .map(RbelElement::getRawStringContent)
              .map(s -> Stream.of(s.split(" ")).skip(1).limit(1).collect(Collectors.joining(",")))
              .orElse("<>"),
          messageParsed.get().getUuid());
    }
    remoteProxyClient.getLastMessageUuid().set(messageParsed.get().getUuid());

    if (remoteProxyClient.messageMatchesFilterCriterion(messageParsed.get())) {
      remoteProxyClient.propagateMessage(messageParsed.get());
    } else {
      remoteProxyClient.removeMessage(messageParsed.get());
    }
  }
}
