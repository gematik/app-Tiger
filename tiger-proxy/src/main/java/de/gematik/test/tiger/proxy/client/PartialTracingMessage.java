/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.client;

import de.gematik.rbellogger.data.RbelHostname;
import java.lang.reflect.Array;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

/**
 * A tracing message which is in the process of being received by the TigerRemoteProxyClient. Can be created by receiving either the
 * metadata for a message (TigerTracingDto) or any of the data-message parts. When the message is complete (all TracingMessageParts
 * and the TigerTracingDto have been received) the underlying message can be processed further.
 */
@Data
@Builder(toBuilder = true)
public class PartialTracingMessage {
    @ToString.Exclude

    private final TigerTracingDto tracingDto;
    private final RbelHostname sender;
    private final RbelHostname receiver;
    @ToString.Exclude
    private final TracingMessageFrame messageFrame;
    private final ZonedDateTime transmissionTime;
    private final ZonedDateTime receivedTime = ZonedDateTime.now();
    private final List<TracingMessagePart> messageParts = new ArrayList<>();

    public boolean isComplete() {
        return !messageParts.isEmpty()
            && messageParts.get(0).getNumberOfMessages() == messageParts.size()
            && tracingDto != null;
    }

    public byte[] buildCompleteContent() {
        byte[] result = new byte[messageParts.stream()
            .map(TracingMessagePart::getData)
            .mapToInt(Array::getLength)
            .sum()];
        int resultIndex = 0;
        for (int i = 0; i < messageParts.size(); i++) {
            System.arraycopy(messageParts.get(i).getData(), 0,
                result, resultIndex, messageParts.get(i).getData().length);
            resultIndex += messageParts.get(i).getData().length;
        }
        return result;
    }
}
