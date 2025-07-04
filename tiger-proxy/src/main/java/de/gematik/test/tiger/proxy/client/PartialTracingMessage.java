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

import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.util.RbelContent;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

/**
 * A tracing message which is in the process of being received by the TigerRemoteProxyClient. Can be
 * created by receiving either the metadata for a message (TigerTracingDto) or any of the
 * data-message parts. When the message is complete (all TracingMessageParts and the TigerTracingDto
 * have been received) the underlying message can be processed further.
 */
@Data
@Builder(toBuilder = true)
public class PartialTracingMessage {
  @ToString.Exclude private final TigerTracingDto tracingDto;

  private final RbelHostname sender;
  private final RbelHostname receiver;
  @ToString.Exclude private final TracingMessageFrame messageFrame;
  private final ZonedDateTime receivedTime = ZonedDateTime.now();
  private final List<TracingMessagePart> messageParts = new ArrayList<>();
  @Builder.Default private final Map<String, Object> additionalInformation = Map.of();

  public boolean isComplete() {
    return !messageParts.isEmpty()
        && messageParts.get(0).getNumberOfMessages() == messageParts.size()
        && tracingDto != null;
  }

  public RbelContent buildCompleteContent() {
    var chunkSize =
        messageParts.stream().mapToInt(part -> part.getData().length).max().orElse(1024);
    var sortedParts =
        messageParts.stream()
            .sorted(Comparator.comparing(TracingMessagePart::getIndex))
            .map(TracingMessagePart::getData)
            .toList();
    return RbelContent.builder().chunkSize(chunkSize).content(sortedParts).build();
  }
}
