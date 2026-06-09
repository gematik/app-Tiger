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
package de.gematik.test.tiger.lib.rbel;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.MessageSortOrder;
import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.time.ZonedDateTime;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RbelMessageRetrieverSortOrderTest {

  @BeforeEach
  void resetConfig() {
    TigerGlobalConfiguration.reset();
    RbelMessageRetriever.VALIDATION_MESSAGE_SORT_ORDER.clearValue();
  }

  @AfterEach
  void cleanUp() {
    RbelMessageRetriever.VALIDATION_MESSAGE_SORT_ORDER.clearValue();
    TigerGlobalConfiguration.reset();
  }

  @Test
  void unset_returnsSequenceAsSafeDefault() {
    assertThat(RbelMessageRetriever.validationMessageSortOrder())
        .isEqualTo(MessageSortOrder.SEQUENCE);
  }

  @Test
  void canonicalUpperCaseValue_isParsed() {
    TigerGlobalConfiguration.putValue(
        "tiger.lib.validation.messageSortOrder", MessageSortOrder.TIMESTAMP.name());

    assertThat(RbelMessageRetriever.validationMessageSortOrder())
        .isEqualTo(MessageSortOrder.TIMESTAMP);
  }

  @Test
  void lowerCaseValueWithWhitespace_isParsed() {
    TigerGlobalConfiguration.putValue("tiger.lib.validation.messageSortOrder", "  timestamp  ");

    assertThat(RbelMessageRetriever.validationMessageSortOrder())
        .isEqualTo(MessageSortOrder.TIMESTAMP);
  }

  @Test
  void unknownValue_fallsBackToSequence_withoutThrowing() {
    TigerGlobalConfiguration.putValue("tiger.lib.validation.messageSortOrder", "GARBAGE");

    assertThat(RbelMessageRetriever.validationMessageSortOrder())
        .isEqualTo(MessageSortOrder.SEQUENCE);
  }

  @Test
  void blankValue_fallsBackToSequence() {
    TigerGlobalConfiguration.putValue("tiger.lib.validation.messageSortOrder", "");

    assertThat(RbelMessageRetriever.validationMessageSortOrder())
        .isEqualTo(MessageSortOrder.SEQUENCE);
  }

  @Test
  void switchingValueAtRuntime_isReflectedImmediately() {
    TigerGlobalConfiguration.putValue(
        "tiger.lib.validation.messageSortOrder", MessageSortOrder.TIMESTAMP.name());
    assertThat(RbelMessageRetriever.validationMessageSortOrder())
        .isEqualTo(MessageSortOrder.TIMESTAMP);

    TigerGlobalConfiguration.putValue(
        "tiger.lib.validation.messageSortOrder", MessageSortOrder.SEQUENCE.name());
    assertThat(RbelMessageRetriever.validationMessageSortOrder())
        .isEqualTo(MessageSortOrder.SEQUENCE);
  }

  /**
   * Tests for {@link
   * RbelMessageRetriever#latestMessage(de.gematik.rbellogger.RbelMessageHistory.MessageHistory,
   * MessageSortOrder)} – the helper that drives the {@code isRequireNewMessage} branch of {@code
   * getInitialElement}.
   */
  @Nested
  class LatestMessageTest {

    private static final ZonedDateTime T0 = ZonedDateTime.parse("2024-01-01T10:00:00Z");
    private static final ZonedDateTime T1 = ZonedDateTime.parse("2024-01-01T10:00:01Z");
    private static final ZonedDateTime T2 = ZonedDateTime.parse("2024-01-01T10:00:02Z");

    private RbelLogger buildLogger() {
      return RbelLogger.build(new RbelConfiguration());
    }

    private RbelElement addMessage(RbelConverter converter, ZonedDateTime timestamp) {
      return converter.parseMessage(
          RandomStringUtils.insecure().nextAlphanumeric(50).getBytes(),
          new RbelMessageMetadata().withTransmissionTime(timestamp));
    }

    @Test
    void emptyHistory_returnsEmpty_inBothModes() {
      var logger = buildLogger();

      assertThat(
              RbelMessageRetriever.latestMessage(
                  logger.getMessageHistory(), MessageSortOrder.SEQUENCE))
          .isEmpty();
      assertThat(
              RbelMessageRetriever.latestMessage(
                  logger.getMessageHistory(), MessageSortOrder.TIMESTAMP))
          .isEmpty();
    }

    @Test
    void inOrderArrival_bothModesReturnSameMessage() {
      var logger = buildLogger();
      var converter = logger.getRbelConverter();
      addMessage(converter, T0);
      addMessage(converter, T1);
      var m2 = addMessage(converter, T2);

      assertThat(
              RbelMessageRetriever.latestMessage(
                  logger.getMessageHistory(), MessageSortOrder.SEQUENCE))
          .contains(m2);
      assertThat(
              RbelMessageRetriever.latestMessage(
                  logger.getMessageHistory(), MessageSortOrder.TIMESTAMP))
          .contains(m2);
    }

    @Test
    void outOfOrderArrival_modesDiverge() {
      var logger = buildLogger();
      var converter = logger.getRbelConverter();

      // Insertion order is T0, T2, T1 → sequence-last is the T1 message,
      // timestamp-last is the T2 message.
      var firstInserted = addMessage(converter, T0);
      var secondInserted = addMessage(converter, T2);
      var lastInserted = addMessage(converter, T1);

      assertThat(
              RbelMessageRetriever.latestMessage(
                  logger.getMessageHistory(), MessageSortOrder.SEQUENCE))
          .contains(lastInserted);
      assertThat(
              RbelMessageRetriever.latestMessage(
                  logger.getMessageHistory(), MessageSortOrder.TIMESTAMP))
          .contains(secondInserted)
          .isNotEqualTo(java.util.Optional.of(firstInserted));
    }
  }

  /**
   * Tests that the new 3-arg {@code MessageHistory#getMessagesAfter(elem, include, sortOrder)} –
   * used by the {@code isStartFromPreviouslyFoundMessage} branch of {@code getInitialElement} –
   * actually walks the history in the requested sort order.
   */
  @Nested
  class MessagesAfterAnchorTest {

    private static final ZonedDateTime T0 = ZonedDateTime.parse("2024-01-01T10:00:00Z");
    private static final ZonedDateTime T1 = ZonedDateTime.parse("2024-01-01T10:00:01Z");
    private static final ZonedDateTime T2 = ZonedDateTime.parse("2024-01-01T10:00:02Z");

    private RbelLogger buildLogger() {
      return RbelLogger.build(new RbelConfiguration());
    }

    private RbelElement addMessage(RbelConverter converter, ZonedDateTime timestamp) {
      return converter.parseMessage(
          RandomStringUtils.insecure().nextAlphanumeric(50).getBytes(),
          new RbelMessageMetadata().withTransmissionTime(timestamp));
    }

    /** Mirrors how {@code getInitialElement} picks the next candidate after a marker. */
    private java.util.Optional<RbelElement> firstAfter(
        de.gematik.rbellogger.RbelMessageHistory.MessageHistory history,
        RbelElement marker,
        MessageSortOrder sortOrder) {
      return history.getMessagesAfter(marker, false, sortOrder).stream().findFirst();
    }

    @Test
    void timestampMode_picksTheChronologicallyNextMessageAfterMarker() {
      var logger = buildLogger();
      var converter = logger.getRbelConverter();

      // Insertion order is m_T2, m_T0, m_T1 – chronological order is m_T0, m_T1, m_T2.
      var mT2 = addMessage(converter, T2);
      var mT0 = addMessage(converter, T0);
      var mT1 = addMessage(converter, T1);

      // After m_T0 chronologically the next is m_T1 …
      assertThat(firstAfter(logger.getMessageHistory(), mT0, MessageSortOrder.TIMESTAMP))
          .contains(mT1);
      // … but in sequence order the next one after m_T0 is m_T1 too because m_T0 has the second
      // sequence number; it just happens to coincide here. After m_T1 in TIMESTAMP we expect m_T2,
      // while in SEQUENCE there is nothing left:
      assertThat(firstAfter(logger.getMessageHistory(), mT1, MessageSortOrder.TIMESTAMP))
          .contains(mT2);
      assertThat(firstAfter(logger.getMessageHistory(), mT1, MessageSortOrder.SEQUENCE)).isEmpty();
    }

    @Test
    void sequenceMode_picksTheNextRegisteredMessage_evenIfChronologicallyEarlier() {
      var logger = buildLogger();
      var converter = logger.getRbelConverter();

      var mT2 = addMessage(converter, T2);
      var mT0 = addMessage(converter, T0);
      var mT1 = addMessage(converter, T1);

      // Sequence-after m_T2 is m_T0 (registered next), and after that m_T1.
      assertThat(firstAfter(logger.getMessageHistory(), mT2, MessageSortOrder.SEQUENCE))
          .contains(mT0);
      assertThat(firstAfter(logger.getMessageHistory(), mT0, MessageSortOrder.SEQUENCE))
          .contains(mT1);
      // In TIMESTAMP mode, after m_T2 there is nothing later chronologically.
      assertThat(firstAfter(logger.getMessageHistory(), mT2, MessageSortOrder.TIMESTAMP)).isEmpty();
    }

    @Test
    void timestampMode_anchorNotInHistory_returnsEmpty() {
      var logger = buildLogger();
      var converter = logger.getRbelConverter();
      addMessage(converter, T0);
      addMessage(converter, T1);

      // A message from an unrelated history.
      var foreign = addMessage(buildLogger().getRbelConverter(), T2);

      assertThat(firstAfter(logger.getMessageHistory(), foreign, MessageSortOrder.TIMESTAMP))
          .isEmpty();
    }
  }
}
