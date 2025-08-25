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

import static de.gematik.rbellogger.util.MemoryConstants.KB;

import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.util.RbelContent;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * A tracing message which is in the process of being received by the TigerRemoteProxyClient. Can be
 * created by receiving either the metadata for a message (TigerTracingDto) or any of the
 * data-message parts. When the message is complete (all TracingMessageParts and the TigerTracingDto
 * have been received) the underlying message can be processed further.
 */
@Data
@Builder(toBuilder = true)
@Slf4j
public class PartialTracingMessage {
  @ToString.Exclude private final TigerTracingDto tracingDto;

  private final RbelHostname sender;
  private final RbelHostname receiver;
  @ToString.Exclude private final TracingMessageFrame messageFrame;
  private final ZonedDateTime receivedTime = ZonedDateTime.now();

  @Getter(AccessLevel.PRIVATE)
  private final ArrayList<TracingMessagePart> messageParts =
      new ArrayList<>(); // sorted by index with null-entries for missing parts

  @Builder.Default private final Map<String, Object> additionalInformation = Map.of();

  private int filledMessageParts; // number of non-null entries in messageParts
  private int expectedMessageParts;

  public void addMessagePart(TracingMessagePart part) {
    synchronized (messageParts) {
      for (int i = messageParts.size(); i <= part.getIndex(); i++) {
        messageParts.add(null); // Fill with nulls for missing parts
      }
      if (messageParts.set(part.getIndex(), part) == null) {
        filledMessageParts++;
      }
      expectedMessageParts = part.getNumberOfMessages();
    }
  }

  public void addMessageParts(PartialTracingMessage other) {
    synchronized (other.messageParts) {
      synchronized (messageParts) {
        other.messageParts.stream().filter(Objects::nonNull).forEach(this::addMessagePart);
      }
    }
  }

  public boolean isComplete() {
    boolean isComplete =
        filledMessageParts > 0 && expectedMessageParts == filledMessageParts && tracingDto != null;
    if (!isComplete) {
      if (log.isTraceEnabled()) {
        synchronized (messageParts) {
          if (filledMessageParts > 0
              && messageParts.size() == expectedMessageParts) { // last part arrived
            var lastPart = messageParts.get(messageParts.size() - 1);
            if (filledMessageParts < expectedMessageParts) {
              log.atTrace()
                  .addArgument(lastPart::getUuid)
                  .addArgument(this::findMissingParts)
                  .log(
                      "Received last part for message with UUID {}. Message still incomplete,"
                          + " missing parts: {}");
            } else if (tracingDto == null) {
              log.atTrace()
                  .addArgument(lastPart::getUuid)
                  .log(
                      "Received complete data for message with UUID {}. Still waiting for"
                          + " metadata");
            }
          }
        }
      }
    }
    return isComplete;
  }

  private List<Integer> findMissingParts() {
    var missingParts = new LinkedList<Integer>();
    var numberOfMissingParts = messageParts.size() - filledMessageParts;
    for (int i = 0; i < messageParts.size() && missingParts.size() < numberOfMissingParts; i++) {
      if (messageParts.get(i) == null) {
        missingParts.add(i + 1); // +1 because message parts are 1-based in logging
      }
    }
    return missingParts;
  }

  public RbelContent buildCompleteContent() {
    var chunkSize = messageParts.stream().mapToInt(part -> part.getData().length).max().orElse(KB);
    var chunks = messageParts.stream().map(TracingMessagePart::getData).toList();
    return RbelContent.builder().chunkSize(chunkSize).content(chunks).build();
  }
}
