/*
 * Copyright (c) 2023 gematik GmbH
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

package de.gematik.test.tiger.proxy.handler;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelBinaryFacet;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.test.tiger.proxy.TigerProxy;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.Arrays;
import org.mockserver.model.BinaryMessage;
import org.mockserver.model.BinaryProxyListener;

@Data
@AllArgsConstructor
@Slf4j
public class BinaryExchangeHandler implements BinaryProxyListener {
    private final TigerProxy tigerProxy;
    private final Map<Pair<SocketAddress, SocketAddress>, byte[]> bufferedParts = new HashMap<>();

    @Override
    public void onProxy(BinaryMessage binaryRequest, CompletableFuture<BinaryMessage> binaryResponseFuture,
        SocketAddress serverAddress, SocketAddress clientAddress) {
        try {
            log.trace("Finalizing binary exchange...");
            convertBinaryMessageOrPushToBuffer(binaryRequest, clientAddress, serverAddress);
            binaryResponseFuture.thenAccept(binaryResponse ->
                convertBinaryMessageOrPushToBuffer(binaryResponse, serverAddress, clientAddress));
        } catch (RuntimeException e) {
            log.warn("Uncaught exception during handling of request", e);
            propagateExceptionMessageSafe(e);
            throw e;
        }
    }

    private void convertBinaryMessageOrPushToBuffer(BinaryMessage message, SocketAddress senderAddress,
        SocketAddress receiverAddress) {
        if (message == null) {
            return;
        }
        Optional<RbelElement> requestOptional = tryToConvertMessageAndBufferUnusedBytes(message, senderAddress, receiverAddress);
        if (requestOptional.isEmpty()) {
            return;
        }
        requestOptional.get().addFacet(RbelMessageTimingFacet.builder()
            .transmissionTime(message.getTimestamp().atZone(ZoneId.systemDefault()))
            .build());
        requestOptional.get().addFacet(new RbelBinaryFacet());
        log.trace("Finalized binary exchange {}",
            requestOptional
                .flatMap(msg -> msg.getFacet(RbelTcpIpMessageFacet.class))
                .map(RbelTcpIpMessageFacet::getSenderHostname)
                .map(Objects::toString)
                .orElse(""));
    }

    private Optional<RbelElement> tryToConvertMessageAndBufferUnusedBytes(BinaryMessage message, SocketAddress senderAddress,
        SocketAddress receiverAddress) {
        final Optional<RbelElement> requestOptional = tryToConvertMessage(message.getBytes(), senderAddress, receiverAddress)
            .or(() -> addBufferToMessage(message, senderAddress, receiverAddress)
                .flatMap(addedBufferBytes -> tryToConvertMessage(addedBufferBytes, senderAddress, receiverAddress)));
        if (requestOptional.isEmpty()) {
            final Pair<SocketAddress, SocketAddress> key = Pair.of(senderAddress, receiverAddress);
            byte[] previouslyBufferedBytes = bufferedParts.get(key);
            if (previouslyBufferedBytes == null) {
                bufferedParts.put(key, message.getBytes());
            } else {
                bufferedParts.put(key, Arrays.concatenate(previouslyBufferedBytes, message.getBytes()));
            }
        }
        return requestOptional;
    }

    private Optional<byte[]> addBufferToMessage(BinaryMessage message, SocketAddress senderAddress, SocketAddress receiverAddress) {
        byte[] bufferedBytes = bufferedParts.get(Pair.of(senderAddress, receiverAddress));
        if (bufferedBytes == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(Arrays.concatenate(bufferedBytes, message.getBytes()));
    }

    private Optional<RbelElement> tryToConvertMessage(byte[] messageContent, SocketAddress senderAddress, SocketAddress receiverAddress) {
        final RbelElement result = getTigerProxy().getRbelLogger().getRbelConverter()
            .parseMessage(messageContent, toRbelHostname(senderAddress),
                toRbelHostname(receiverAddress), Optional.empty());
        if (result.getFacets().size() <= 1) {
            getTigerProxy().getRbelLogger().getRbelConverter().removeMessage(result);
            return Optional.empty();
        }
        return Optional.of(result);
    }

    private void propagateExceptionMessageSafe(RuntimeException exception) {
        try {
            tigerProxy.propagateException(exception);
        } catch (Exception handlingException) {
            log.warn("While propagating an exception another error occured (ignoring):", handlingException);
        }
    }

    private RbelHostname toRbelHostname(SocketAddress socketAddress) {
        if (socketAddress instanceof InetSocketAddress) {
            return RbelHostname.builder()
                .hostname(((InetSocketAddress) socketAddress).getHostName())
                .port(((InetSocketAddress) socketAddress).getPort())
                .build();
        } else {
            log.warn("Incompatible socketAddress encountered: " + socketAddress.getClass().getSimpleName());
            return null;
        }
    }
}
