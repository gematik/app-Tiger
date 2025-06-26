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

import java.lang.reflect.Type;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;

@RequiredArgsConstructor
@Slf4j
class ErrorsStompHandler implements StompFrameHandler {

  private final TigerRemoteProxyClient remoteProxyClient;

  @Override
  public Type getPayloadType(StompHeaders stompHeaders) {
    return TigerExceptionDto.class;
  }

  @Override
  public void handleFrame(StompHeaders stompHeaders, Object frameContent) {
    if (frameContent instanceof TigerExceptionDto exceptionDto) {
      log.warn(
          "Received remote exception: ({}) {}: {} ",
          exceptionDto.getClassName(),
          exceptionDto.getMessage(),
          exceptionDto.getStacktrace());
      remoteProxyClient.getReceivedRemoteExceptions().add(exceptionDto);
    }
  }
}
