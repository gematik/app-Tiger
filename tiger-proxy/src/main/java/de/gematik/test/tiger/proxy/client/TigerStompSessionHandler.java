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

import de.gematik.test.tiger.proxy.handler.TigerExceptionUtils;
import java.net.ConnectException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.stomp.*;

@RequiredArgsConstructor
@Slf4j
class TigerStompSessionHandler extends StompSessionHandlerAdapter {

  private final TigerRemoteProxyClient remoteProxyClient;
  @Getter private TracingStompHandler tracingStompHandler;
  @Getter private DataStompHandler dataStompHandler;
  @Getter private ErrorsStompHandler errorStompHandler;
  @Setter private Runnable onConnectedCallback;

  @Override
  public void afterConnected(final StompSession stompSession, final StompHeaders stompHeaders) {
    log.info("Connecting to tracing point {}", remoteProxyClient.getRemoteProxyUrl());

    tracingStompHandler = new TracingStompHandler(remoteProxyClient);
    stompSession.subscribe(TigerRemoteProxyClient.WS_TRACING, tracingStompHandler);
    dataStompHandler = new DataStompHandler(remoteProxyClient);
    stompSession.subscribe(TigerRemoteProxyClient.WS_DATA, dataStompHandler);
    errorStompHandler = new ErrorsStompHandler(remoteProxyClient);
    stompSession.subscribe(TigerRemoteProxyClient.WS_ERRORS, errorStompHandler);

    if (onConnectedCallback != null) {
      onConnectedCallback.run();
    }
  }

  @Override
  public void handleException(
      final StompSession stompSession,
      final StompCommand stompCommand,
      final StompHeaders stompHeaders,
      final byte[] bytes,
      final Throwable throwable) {
    log.error(
        "handle exception with remote url '{}': {}",
        remoteProxyClient.getRemoteProxyUrl(),
        new String(bytes),
        throwable);
    throw new TigerRemoteProxyClientException(throwable);
  }

  @Override
  public void handleTransportError(final StompSession session, final Throwable exception) {
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
          "handle transport error from url '{}'",
          remoteProxyClient.getRemoteProxyUrl(),
          exception);
      throw new TigerRemoteProxyClientException(exception);
    }
  }
}
