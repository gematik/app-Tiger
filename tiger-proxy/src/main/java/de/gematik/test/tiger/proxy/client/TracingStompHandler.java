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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyRoutingException;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;

@RequiredArgsConstructor
@Slf4j
class TracingStompHandler implements StompFrameHandler {

  private final TigerRemoteProxyClient remoteProxyClient;

  @Override
  public Type getPayloadType(@Nullable StompHeaders stompHeaders) {
    return TigerTracingDto.class;
  }

  @Override
  public void handleFrame(@Nullable StompHeaders stompHeaders, Object frameContent) {
    log.atTrace()
        .addArgument(
            () ->
                Optional.ofNullable(frameContent)
                    .map(Object::getClass)
                    .map(Class::getSimpleName)
                    .orElse("<>"))
        .addArgument(remoteProxyClient.getName().orElse("<>"))
        .addArgument(
            () -> {
              try {
                return new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(frameContent);
              } catch (JsonProcessingException e) {
                return "<>";
              }
            })
        .log("Received new frame of type {} in proxy {} with content: {}");
    if (frameContent instanceof TigerTracingDto tigerTracingDto) {
      CompletableFuture.runAsync(
              () -> registerNewMessage(tigerTracingDto), remoteProxyClient.getMeshHandlerPool())
          .exceptionally(
              e -> {
                remoteProxyClient.propagateException(
                    new TigerProxyRoutingException(
                        "Error while handling message: " + e.getMessage(),
                        tigerTracingDto.getSender(),
                        tigerTracingDto.getReceiver(),
                        e));
                return null;
              });
    }
  }

  private void registerNewMessage(TigerTracingDto tigerTracingDto) {
    var messageFrame = new TracingMessageFrame(remoteProxyClient);
    messageFrame.setMessage(
        PartialTracingMessage.builder()
            .tracingDto(tigerTracingDto)
            .receiver(tigerTracingDto.getReceiver())
            .sender(tigerTracingDto.getSender())
            .messageFrame(messageFrame)
            .additionalInformation(tigerTracingDto.getAdditionalInformation())
            .build());
    remoteProxyClient.initOrUpdateMessagePart(
        tigerTracingDto.getMessageUuid(), messageFrame.getMessage());
  }
}
