/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
