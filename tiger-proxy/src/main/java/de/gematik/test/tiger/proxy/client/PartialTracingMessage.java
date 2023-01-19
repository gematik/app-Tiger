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

package de.gematik.test.tiger.proxy.client;

import de.gematik.rbellogger.data.RbelHostname;
import java.lang.reflect.Array;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder(toBuilder = true)
public class PartialTracingMessage {
    @ToString.Exclude

    private final TigerTracingDto tracingDto;
    private final RbelHostname sender;
    private final RbelHostname receiver;
    @ToString.Exclude
    private final TracingMessagePair messagePair;
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
