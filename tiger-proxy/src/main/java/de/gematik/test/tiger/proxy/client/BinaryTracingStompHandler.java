/*
 *
 * Copyright 2021-2026 gematik GmbH
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

import de.gematik.test.tiger.proxy.exceptions.TigerProxyException;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;

/**
 * Receives the frames of {@link BinaryTracingFrame}, the data of the messages. A frame is a piece
 * of a message and goes to the same assembler as the pieces of the legacy destination, so that the
 * messages are assembled, ordered and parsed in the same way. The metadata of a message comes on
 * the destination that every Tiger proxy has, see {@link TracingStompHandler}.
 */
@RequiredArgsConstructor
@Slf4j
class BinaryTracingStompHandler implements StompFrameHandler {

  private final TigerRemoteProxyClient remoteProxyClient;

  @Override
  public Type getPayloadType(StompHeaders headers) {
    return byte[].class;
  }

  @Override
  public void handleFrame(StompHeaders headers, Object frameContent) {
    if (!(frameContent instanceof byte[] body)) {
      return;
    }
    CompletableFuture.runAsync(() -> receive(headers, body), remoteProxyClient.getMeshHandlerPool())
        .exceptionally(
            e -> {
              remoteProxyClient.propagateException(
                  new TigerProxyException(
                      "Error while handling message '"
                          + headers.getFirst(BinaryTracingFrame.HEADER_MESSAGE_UUID)
                          + "': "
                          + e.getMessage(),
                      e));
              return null;
            });
  }

  private void receive(StompHeaders headers, byte[] body) {
    var part = BinaryTracingFrame.part(headers, body);
    log.atTrace()
        .addArgument(() -> part.getIndex() + 1)
        .addArgument(part::getNumberOfMessages)
        .addArgument(part::getUuid)
        .addArgument(remoteProxyClient::getName)
        .log("Received part {} of {} for UUID {} at {}");
    remoteProxyClient.getMessageAssembler().receivePart(part);
  }
}
