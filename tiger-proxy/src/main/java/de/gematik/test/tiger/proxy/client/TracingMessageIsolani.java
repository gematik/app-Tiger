/*
 * Copyright (c) 2024 gematik GmbH
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
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.facet.TigerNonPairedMessageFacet;
import de.gematik.rbellogger.file.RbelFileWriter;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.json.JSONObject;

@Data
@Slf4j
public class TracingMessageIsolani implements TracingMessageFrame {

  private PartialTracingMessage message;
  @ToString.Exclude private final TigerRemoteProxyClient remoteProxyClient;

  @Override
  public void checkForCompletePairAndPropagateIfComplete() {
    if (message != null && message.isComplete()) {
      parseAndPropagate();
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

    messageParsed
        .get()
        .thenAccept(
            msg -> {
              try {
              doPostConversion(msg);

              } catch (RuntimeException e){
                log.error(
                  "{} - Error while processing message with UUID {}",
                  remoteProxyClient.proxyName(),
                  message.getTracingDto().getRequestUuid(),
                  e);
                throw e;
              }
            })
      .exceptionally(
        e -> {
          log.error(
            "{} - Error while processing message with UUID {}",
            remoteProxyClient.proxyName(),
            message.getTracingDto().getRequestUuid(),
            e);
          return null;
        });  }

  private void doPostConversion(RbelElement msg) {
    msg.addOrReplaceFacet(
      msg.getFacetOrFail(RbelTcpIpMessageFacet.class).toBuilder()
        .sequenceNumber(message.getTracingDto().getSequenceNumberRequest())
        .receivedFromRemoteWithUrl(remoteProxyClient.getRemoteProxyUrl())
        .build());

    triggerPostConversionListener(msg);

    msg.addFacet(new TigerNonPairedMessageFacet());

    if (log.isTraceEnabled()) {
      log.trace(
          "{}Received isolani message to {} (UUID {})",
          remoteProxyClient.proxyName(),
          Optional.of(msg)
              .map(RbelElement::getRawStringContent)
              .map(
                  s ->
                      Stream.of(s.split(" "))
                          .skip(1)
                          .limit(1)
                          .collect(Collectors.joining(",")))
              .orElse("<>"),
          msg.getUuid());
    }
    remoteProxyClient.getLastMessageUuid().set(msg.getUuid());

    if (remoteProxyClient.messageMatchesFilterCriterion(msg)) {
      remoteProxyClient.propagateMessage(msg);
    } else {
      remoteProxyClient.removeMessage(msg);
    }
  }

  private void triggerPostConversionListener(RbelElement msg) {
    RbelFileWriter.DEFAULT_POST_CONVERSION_LISTENER.forEach(
        listener ->
            listener.performMessagePostConversionProcessing(
                msg,
                remoteProxyClient.getRbelLogger().getRbelConverter(),
                new JSONObject(this.message.getAdditionalInformation())));
  }
}
