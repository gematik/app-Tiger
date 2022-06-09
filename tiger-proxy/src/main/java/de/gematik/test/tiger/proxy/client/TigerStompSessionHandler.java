/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.proxy.client;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.stomp.*;

@RequiredArgsConstructor
@Slf4j
class TigerStompSessionHandler extends StompSessionHandlerAdapter {

    private final TigerRemoteProxyClient remoteProxyClient;
    @Getter
    private TracingStompHandler tracingStompHandler;
    @Getter
    private DataStompHandler dataStompHandler;
    @Getter
    private ErrorsStompHandler errorStompHandler;

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
    public void handleException(StompSession stompSession, StompCommand stompCommand, StompHeaders stompHeaders,
        byte[] bytes, Throwable throwable) {
        log.error("handle exception with remote url '{}': {}, {}", remoteProxyClient.getRemoteProxyUrl(),
            new String(bytes), throwable);
        throw new TigerRemoteProxyClientException(throwable);
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
        if (exception instanceof ConnectionLostException) {
            log.warn("Remote client lost connection to url {}. Reconnecting...", remoteProxyClient.getRemoteProxyUrl());
            remoteProxyClient.connectToRemoteUrl( this,
                remoteProxyClient.getTigerProxyConfiguration().getConnectionTimeoutInSeconds(),
                true);
        } else {
            log.error("handle transport error from url '{}': {}", remoteProxyClient.getRemoteProxyUrl(), exception);
            throw new TigerRemoteProxyClientException(exception);
        }
    }
}
