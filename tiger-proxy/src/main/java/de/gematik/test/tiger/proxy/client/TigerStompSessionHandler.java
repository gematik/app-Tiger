/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.client;

import de.gematik.test.tiger.proxy.handler.TigerExceptionUtils;
import java.net.ConnectException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.stomp.*;

@RequiredArgsConstructor
@Slf4j
class TigerStompSessionHandler extends StompSessionHandlerAdapter {

  private final TigerRemoteProxyClient remoteProxyClient;
  @Getter private TracingStompHandler tracingStompHandler;
  @Getter private DataStompHandler dataStompHandler;
  @Getter private ErrorsStompHandler errorStompHandler;

  @Override
  public void afterConnected(StompSession stompSession, StompHeaders stompHeaders) {
    log.info("Connecting to tracing point {}", remoteProxyClient.getRemoteProxyUrl());

    tracingStompHandler = new TracingStompHandler(remoteProxyClient);
    stompSession.subscribe(TigerRemoteProxyClient.WS_TRACING, tracingStompHandler);
    dataStompHandler = new DataStompHandler(remoteProxyClient);
    stompSession.subscribe(TigerRemoteProxyClient.WS_DATA, dataStompHandler);
    errorStompHandler = new ErrorsStompHandler(remoteProxyClient);
    stompSession.subscribe(TigerRemoteProxyClient.WS_ERRORS, errorStompHandler);
  }

  @Override
  public void handleException(
      StompSession stompSession,
      StompCommand stompCommand,
      StompHeaders stompHeaders,
      byte[] bytes,
      Throwable throwable) {
    log.error(
        "handle exception with remote url '{}': {}, {}",
        remoteProxyClient.getRemoteProxyUrl(),
        new String(bytes),
        throwable);
    throw new TigerRemoteProxyClientException(throwable);
  }

  @Override
  public void handleTransportError(StompSession session, Throwable exception) {
    if (exception instanceof ConnectionLostException) {
      if (remoteProxyClient.isShuttingDown()) {
        log.warn(
            "Remote client lost connection to url {} in session {} (isConnected = {}). Client in"
                + " shutdown, skipping reconnect!",
            remoteProxyClient.getRemoteProxyUrl(),
            session.getSessionId(),
            session.isConnected());
        return;
      }
      log.warn(
          "Remote client lost connection to url {} in session {} (isConnected = {})."
              + " Reconnecting...",
          remoteProxyClient.getRemoteProxyUrl(),
          session.getSessionId(),
          session.isConnected());
      remoteProxyClient.connectToRemoteUrl(
          this,
          remoteProxyClient.getTigerProxyConfiguration().getConnectionTimeoutInSeconds(),
          true);
    } else if (TigerExceptionUtils.getCauseWithType(exception, ConnectException.class)
        .filter(e -> "Connection refused".equals(e.getMessage()))
        .isPresent()) {
      if (remoteProxyClient.isShuttingDown()) {
        log.debug(
            "Connection refused from remote tracing partner. Ignoring since we are in shutdown");
      } else {
        log.debug("Connection refused from remote tracing partner! We are not in shutdown");
        throw new TigerRemoteProxyClientException(exception);
      }
    } else {
      log.error(
          "handle transport error from url '{}': {}",
          remoteProxyClient.getRemoteProxyUrl(),
          exception);
      throw new TigerRemoteProxyClientException(exception);
    }
  }
}
