/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package de.gematik.rbellogger;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.facets.timing.RbelMessageTimingFacet;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

class RbelMessageHistoryTimestampTest {

  private static final ZonedDateTime T0 = ZonedDateTime.parse("2024-01-01T10:00:00Z");
  private static final ZonedDateTime T1 = ZonedDateTime.parse("2024-01-01T10:00:01Z");
  private static final ZonedDateTime T2 = ZonedDateTime.parse("2024-01-01T10:00:02Z");

  private RbelLogger buildLogger() {
    return RbelLogger.build(new RbelConfiguration());
  }

  private RbelElement addMessage(RbelConverter converter, ZonedDateTime timestamp) {
    return converter.parseMessage(
        RandomStringUtils.insecure().nextAlphanumeric(100).getBytes(),
        new RbelMessageMetadata().withTransmissionTime(timestamp));
  }

  @Test
  void inOrderTimestamps_sortedListMatchesInsertionOrder() {
    var logger = buildLogger();
    var converter = logger.getRbelConverter();

    var m0 = addMessage(converter, T0);
    var m1 = addMessage(converter, T1);
    var m2 = addMessage(converter, T2);

    assertThat(logger.getMessageHistory().getMessagesByTimestamp()).containsExactly(m0, m1, m2);
  }

  @Test
  void outOfOrderTimestamps_sortedListReturnsByTimestamp() {
    var logger = buildLogger();
    var converter = logger.getRbelConverter();

    var m2 = addMessage(converter, T2);
    var m0 = addMessage(converter, T0);
    var m1 = addMessage(converter, T1);

    assertThat(logger.getMessagesByTimestamp()).containsExactly(m0, m1, m2);
  }

  @Test
  void sameTimestamp_sortedBySequenceNumber() {
    var logger = buildLogger();
    var converter = logger.getRbelConverter();

    var m0 = addMessage(converter, T0);
    var m1 = addMessage(converter, T0);
    var m2 = addMessage(converter, T0);

    assertThat(logger.getMessagesByTimestamp()).containsExactly(m0, m1, m2);
  }

  @Test
  void addMessageToHistory_eagerlySetsTimingFacet() {
    var logger = buildLogger();
    var converter = logger.getRbelConverter();

    var message = addMessage(converter, T1);

    assertThat(message.getFacet(RbelMessageTimingFacet.class))
        .isPresent()
        .get()
        .extracting(RbelMessageTimingFacet::getTransmissionTime)
        .isEqualTo(T1);
  }

  @Test
  void addMessageWithoutTimestamp_usesNowAsFallback() {
    var logger = buildLogger();
    var converter = logger.getRbelConverter();

    var before = ZonedDateTime.now();
    var message = converter.parseMessage("test".getBytes(), new RbelMessageMetadata());
    var after = ZonedDateTime.now();

    var facet = message.getFacet(RbelMessageTimingFacet.class);
    assertThat(facet).isPresent();
    assertThat(facet.get().getTransmissionTime()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
  }

  @Test
  void removeMessage_removesFromTimestampSortedSet() {
    var logger = buildLogger();
    var converter = logger.getRbelConverter();

    var m0 = addMessage(converter, T0);
    var m1 = addMessage(converter, T1);
    var m2 = addMessage(converter, T2);

    converter.removeMessage(m1);

    assertThat(logger.getMessagesByTimestamp()).containsExactly(m0, m2);
  }

  @Test
  void clearAllMessages_emptiesTimestampSortedSet() {
    var logger = buildLogger();
    var converter = logger.getRbelConverter();

    addMessage(converter, T0);
    addMessage(converter, T1);
    addMessage(converter, T2);

    logger.clearAllMessages();

    assertThat(logger.getMessagesByTimestamp()).isEmpty();
  }

  @Test
  void bufferEviction_removesFromTimestampSortedSet() {
    var logger =
        RbelLogger.build(
            RbelConfiguration.builder().manageBuffer(true).rbelBufferSizeInMb(1).build());
    var converter = logger.getRbelConverter();

    var largeContent = RandomStringUtils.insecure().nextAlphanumeric(5000);
    int maxBufferSize = 1024 * 1024;
    int messagesForOverflow = (maxBufferSize / largeContent.getBytes().length) + 5;

    List<RbelElement> allMessages = new ArrayList<>();
    for (int i = 0; i < messagesForOverflow; i++) {
      allMessages.add(
          converter.parseMessage(
              largeContent.getBytes(),
              new RbelMessageMetadata().withTransmissionTime(T0.plusSeconds(i))));
    }

    var sortedMessages = logger.getMessagesByTimestamp();

    // Sorted list should be in timestamp order
    for (int i = 1; i < sortedMessages.size(); i++) {
      var prev =
          sortedMessages
              .get(i - 1)
              .getFacetOrFail(RbelMessageTimingFacet.class)
              .getTransmissionTime();
      var curr =
          sortedMessages.get(i).getFacetOrFail(RbelMessageTimingFacet.class).getTransmissionTime();
      assertThat(curr).isAfterOrEqualTo(prev);
    }

    // Some messages should have been evicted
    assertThat(sortedMessages.size()).isLessThan(allMessages.size());
  }

  // ---------------------------------------------------------------------------
  // getMessagesAfter(element, includeElement, MessageSortOrder)
  // ---------------------------------------------------------------------------

  @Test
  void getMessagesAfter_sequenceMode_includingAnchor() {
    var logger = buildLogger();
    var converter = logger.getRbelConverter();

    var m0 = addMessage(converter, T0);
    var m1 = addMessage(converter, T1);
    var m2 = addMessage(converter, T2);

    assertThat(logger.getMessageHistory().getMessagesAfter(m1, true, MessageSortOrder.SEQUENCE))
        .containsExactly(m1, m2)
        .doesNotContain(m0);
  }

  @Test
  void getMessagesAfter_sequenceMode_excludingAnchor() {
    var logger = buildLogger();
    var converter = logger.getRbelConverter();

    var m0 = addMessage(converter, T0);
    var m1 = addMessage(converter, T1);
    var m2 = addMessage(converter, T2);

    assertThat(logger.getMessageHistory().getMessagesAfter(m1, false, MessageSortOrder.SEQUENCE))
        .containsExactly(m2)
        .doesNotContain(m0, m1);
  }

  @Test
  void getMessagesAfter_timestampMode_inOrderInsertion() {
    var logger = buildLogger();
    var converter = logger.getRbelConverter();

    addMessage(converter, T0);
    var m1 = addMessage(converter, T1);
    var m2 = addMessage(converter, T2);

    assertThat(logger.getMessageHistory().getMessagesAfter(m1, true, MessageSortOrder.TIMESTAMP))
        .containsExactly(m1, m2);
  }

  @Test
  void getMessagesAfter_timestampMode_outOfOrderInsertion_returnsByTimestampNotSequence() {
    var logger = buildLogger();
    var converter = logger.getRbelConverter();

    // Insertion order is T2, T0, T1 but the chronological order is T0, T1, T2.
    var m2 = addMessage(converter, T2);
    var m0 = addMessage(converter, T0);
    var m1 = addMessage(converter, T1);

    // Anchor m0 is the chronologically first message, so "after m0 inclusive" must be
    // m0, m1, m2 in chronological order – NOT in insertion (sequence) order.
    assertThat(logger.getMessageHistory().getMessagesAfter(m0, true, MessageSortOrder.TIMESTAMP))
        .containsExactly(m0, m1, m2);

    // Sanity check: the sequence-keyed view still returns insertion order.
    assertThat(logger.getMessageHistory().getMessagesAfter(m0, true, MessageSortOrder.SEQUENCE))
        .containsExactly(m0, m1);
  }

  @Test
  void getMessagesAfter_timestampMode_excludingAnchor() {
    var logger = buildLogger();
    var converter = logger.getRbelConverter();

    var m0 = addMessage(converter, T0);
    var m1 = addMessage(converter, T1);
    var m2 = addMessage(converter, T2);

    assertThat(logger.getMessageHistory().getMessagesAfter(m1, false, MessageSortOrder.TIMESTAMP))
        .containsExactly(m2)
        .doesNotContain(m0, m1);
  }

  @Test
  void getMessagesAfter_timestampMode_anchorWithSameTimestampAsOthers_usesSequenceTieBreaker() {
    var logger = buildLogger();
    var converter = logger.getRbelConverter();

    // Three messages all with timestamp T0 – tie breaker is sequence number.
    var m0 = addMessage(converter, T0);
    var m1 = addMessage(converter, T0);
    var m2 = addMessage(converter, T0);

    assertThat(logger.getMessageHistory().getMessagesAfter(m1, true, MessageSortOrder.TIMESTAMP))
        .containsExactly(m1, m2)
        .doesNotContain(m0);
    assertThat(logger.getMessageHistory().getMessagesAfter(m1, false, MessageSortOrder.TIMESTAMP))
        .containsExactly(m2)
        .doesNotContain(m0, m1);
  }

  @Test
  void getMessagesAfter_timestampMode_anchorNotInHistory_usedAsTimestampCutoff() {
    var logger = buildLogger();
    var converter = logger.getRbelConverter();

    var m0 = addMessage(converter, T0);
    var m2 = addMessage(converter, T2);

    // Build a foreign element (timestamp T1) that has never been added to this history.
    // It must still serve as a valid cutoff: messages with a (timestamp, seqNr) tuple greater
    // than the foreign anchor are returned, mirroring the SEQUENCE branch's behavior for
    // previously removed anchors.
    var loggerOther = buildLogger();
    var foreign = addMessage(loggerOther.getRbelConverter(), T1);

    assertThat(
            logger.getMessageHistory().getMessagesAfter(foreign, true, MessageSortOrder.TIMESTAMP))
        .containsExactly(m2);
    assertThat(
            logger.getMessageHistory().getMessagesAfter(foreign, false, MessageSortOrder.TIMESTAMP))
        .containsExactly(m2);
    assertThat(m0).isNotNull(); // m0 (T0) is before the foreign cutoff (T1) and must be excluded
  }

  @Test
  void getMessagesAfter_timestampMode_anchorIsLastChronologically_returnsOnlyAnchor() {
    var logger = buildLogger();
    var converter = logger.getRbelConverter();

    addMessage(converter, T0);
    addMessage(converter, T1);
    var last = addMessage(converter, T2);

    assertThat(logger.getMessageHistory().getMessagesAfter(last, true, MessageSortOrder.TIMESTAMP))
        .containsExactly(last);
    assertThat(logger.getMessageHistory().getMessagesAfter(last, false, MessageSortOrder.TIMESTAMP))
        .isEmpty();
  }
}
