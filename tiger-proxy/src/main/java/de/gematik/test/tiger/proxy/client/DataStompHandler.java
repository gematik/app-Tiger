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
 */

package de.gematik.test.tiger.proxy.client;

import java.lang.reflect.Type;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;

@RequiredArgsConstructor
@Slf4j
class DataStompHandler implements StompFrameHandler {

  private final TigerRemoteProxyClient remoteProxyClient;

  @Override
  public Type getPayloadType(StompHeaders stompHeaders) {
    return TracingMessagePart.class;
  }

  @Override
  public void handleFrame(StompHeaders stompHeaders, Object frameContent) {
    if (log.isTraceEnabled()) {
      log.trace(
          "Received new frame of type {} in proxy {}",
          frameContent.getClass().getSimpleName(),
          remoteProxyClient.getName().orElse("<>"));
    }
    if (frameContent instanceof TracingMessagePart tracingMessagePart) {
      log.trace(
          "Received part {} of {} for UUID {}",
          tracingMessagePart.getIndex() + 1,
          tracingMessagePart.getNumberOfMessages(),
          tracingMessagePart.getUuid());
      remoteProxyClient.receiveNewMessagePart(tracingMessagePart);
    }
  }
}
