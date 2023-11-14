/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
    if (frameContent instanceof TigerExceptionDto) {
      final TigerExceptionDto exceptionDto = (TigerExceptionDto) frameContent;
      log.warn(
          "Received remote exception: ({}) {}: {} ",
          exceptionDto.getClassName(),
          exceptionDto.getMessage(),
          exceptionDto.getStacktrace());
      remoteProxyClient.getReceivedRemoteExceptions().add(exceptionDto);
    }
  }
}
