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
package de.gematik.test.tiger.proxy.client;

import static de.gematik.rbellogger.data.RbelMessageMetadata.PAIRED_MESSAGE_UUID;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import de.gematik.rbellogger.facets.http.RbelHttpResponseFacet;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Data;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Data
@Slf4j
public class TracingMessageFrame {

  private PartialTracingMessage message;
  private AtomicBoolean addedToBuffer = new AtomicBoolean(false);
  @ToString.Exclude private final TigerRemoteProxyClient remoteProxyClient;

  public void checkForCompletePairAndPropagateIfComplete() {
    if (message != null && message.isComplete()) {
      queueParsingTask();
    }
  }

  private void queueParsingTask() {
    String previousMessageUuid =
        (String)
            message
                .getAdditionalInformation()
                .get(RbelMessageMetadata.PREVIOUS_MESSAGE_UUID.getKey());
    if (previousMessageUuid != null) {
      log.atTrace()
          .addArgument(message.getTracingDto()::getMessageUuid)
          .addArgument(() -> previousMessageUuid)
          .log("Queueing {} behind {}");
      remoteProxyClient.scheduleAfterMessage(
          previousMessageUuid, this::parseThisMessage, message.getTracingDto().getMessageUuid());
    } else {
      log.atTrace()
          .addArgument(message.getTracingDto()::getMessageUuid)
          .log("No previous message UUID found, parsing message {} immediately");
      parseThisMessage();
    }
  }

  private synchronized void parseThisMessage() {
    if (addedToBuffer.get()) {
      log.info("NOTAUSGANG für {}", message.getTracingDto().getMessageUuid());
      return;
    }
    log.atTrace()
        .addArgument(() -> message.getTracingDto().getMessageUuid())
        .log("Parsing tracing message {}");
    remoteProxyClient.tryParseMessages(
        message, el -> el.addFacet(new MeshMessagePostProcessingFacet(message, remoteProxyClient)));
    addedToBuffer.set(true);
  }

  @Value
  public static class MeshMessagePostProcessingFacet implements RbelFacet {
    PartialTracingMessage message;
    TigerRemoteProxyClient remoteProxyClient;

    @Override
    public RbelMultiMap<RbelElement> getChildElements() {
      return new RbelMultiMap<>();
    }
  }

  @ConverterInfo
  public static class TracingMessagePreparationHandler extends RbelConverterPlugin {

    @Override
    public RbelConversionPhase getPhase() {
      return RbelConversionPhase.PREPARATION;
    }

    @Override
    public int getPriority() {
      return -10000;
    }

    @Override
    public void consumeElement(RbelElement msg, RbelConversionExecutor converter) {
      msg.getFacet(MeshMessagePostProcessingFacet.class)
          .ifPresent(
              facet -> {
                val metadata =
                    msg.getFacet(RbelMessageMetadata.class).orElseGet(RbelMessageMetadata::new);
                facet.getMessage().getAdditionalInformation().forEach(metadata::addMetadata);
                metadata
                    .withSender(facet.getMessage().getSender())
                    .withReceiver(facet.getMessage().getReceiver());
              });
    }
  }

  @ConverterInfo
  public static class TracingMessagePostConversionHandler extends RbelConverterPlugin {

    @Override
    public RbelConversionPhase getPhase() {
      return RbelConversionPhase.CONTENT_ENRICHMENT;
    }

    @Override
    public void consumeElement(RbelElement msg, RbelConversionExecutor converter) {
      final Optional<MeshMessagePostProcessingFacet> facet =
          msg.getFacet(MeshMessagePostProcessingFacet.class);
      if (facet.isEmpty() || !msg.hasFacet(RbelTcpIpMessageFacet.class)) {
        return;
      }
      msg.removeFacetsOfType(MeshMessagePostProcessingFacet.class);
      PartialTracingMessage message = facet.get().getMessage();
      TigerRemoteProxyClient remoteProxyClient = facet.get().getRemoteProxyClient();

      expandMeshTransmissionHistory(msg, message, remoteProxyClient);

      msg.getFacet(RbelMessageMetadata.class)
          .flatMap(PAIRED_MESSAGE_UUID::getValue)
          .flatMap(converter::findMessageByUuid)
          .ifPresent(
              previousMessage -> {
                TracingMessagePairFacet pair;
                if (previousMessage.hasFacet(RbelHttpResponseFacet.class)) {
                  pair = new TracingMessagePairFacet(previousMessage, msg);
                } else {
                  pair = new TracingMessagePairFacet(msg, previousMessage);
                }
                msg.addOrReplaceFacet(pair);
                previousMessage.addOrReplaceFacet(pair);
              });

      if (!remoteProxyClient.messageMatchesFilterCriterion(msg)) {
        remoteProxyClient.removeMessage(msg);
      }
    }

    private void expandMeshTransmissionHistory(
        RbelElement msg, PartialTracingMessage message, TigerRemoteProxyClient remoteProxyClient) {
      msg.addOrReplaceFacet(
          msg.getFacetOrFail(RbelTcpIpMessageFacet.class).toBuilder()
              .receivedFromRemoteWithUrl(remoteProxyClient.getRemoteProxyUrl())
              .build());

      if (message.getTracingDto().getProxyTransmissionHistory() != null) {
        msg.addFacet(message.getTracingDto().getProxyTransmissionHistory());
      } else {
        log.atTrace()
            .addArgument(message.getTracingDto()::getMessageUuid)
            .log("No proxy transmission history found for message {}");
      }
    }
  }
}
