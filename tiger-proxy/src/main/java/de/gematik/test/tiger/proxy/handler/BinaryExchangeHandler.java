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

import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageKind;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.util.RbelSocketAddress;
import de.gematik.test.tiger.mockserver.model.BinaryMessage;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyRoutingException;
import de.gematik.test.tiger.proxy.exceptions.TigerRoutingErrorFacet;
import java.time.ZoneId;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Data
@Slf4j
public class BinaryExchangeHandler {

  private final TigerProxy tigerProxy;
  private final MultipleBinaryConnectionParser connectionParser;

  public BinaryExchangeHandler(TigerProxy tigerProxy) {
    this.tigerProxy = tigerProxy;
    this.connectionParser = new MultipleBinaryConnectionParser(tigerProxy, this);
  }

  public void onProxy(
      BinaryMessage binaryMessage,
      RbelSocketAddress serverAddress,
      RbelSocketAddress clientAddress,
      RbelMessageKind messageKind) {
    connectionParser.addToBuffer(
        clientAddress,
        serverAddress,
        binaryMessage.getBytes(),
        binaryMessage.getTimestamp().atZone(ZoneId.systemDefault()),
        messageKind);
  }

  public void propagateExceptionMessageSafe(
      Throwable exception, RbelSocketAddress senderAddress, RbelSocketAddress receiverAddress) {
    try {

      final TigerProxyRoutingException routingException =
          new TigerProxyRoutingException(
              "Exception during handling of binary request: " + exception.getMessage(),
              senderAddress,
              receiverAddress,
              exception);

      val message = new RbelElement(new byte[] {}, null);
      message.addFacet(new TigerRoutingErrorFacet(routingException));
      try {
        tigerProxy
            .getRbelLogger()
            .getRbelConverter()
            .parseMessage(
                message,
                new RbelMessageMetadata()
                    .withSender(senderAddress)
                    .withReceiver(receiverAddress)
                    .withTransmissionTime(routingException.getTimestamp()));
      } finally {
        if (message.getConversionPhase() != RbelConversionPhase.DELETED) {
          log.error(routingException.getMessage(), routingException);
          tigerProxy.propagateException(exception);
        }
      }
    } catch (Exception handlingException) {
      log.warn(
          "While propagating an exception another error occurred (ignoring):", handlingException);
    }
  }

  public void waitForAllParsingTasksToBeFinished() {
    connectionParser.waitForAllParsingTasksToBeFinished();
  }
}
