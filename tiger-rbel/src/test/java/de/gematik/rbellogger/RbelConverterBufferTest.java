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
package de.gematik.rbellogger;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static de.gematik.rbellogger.util.MemoryConstants.MB;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

class RbelConverterBufferTest {

  @Test
  void emptyBuffer_shouldNotContainMessages() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/jwtMessage.curl");

    final RbelLogger rbelLogger =
        RbelLogger.build(
            RbelConfiguration.builder().manageBuffer(true).rbelBufferSizeInMb(0).build());
    final RbelElement convertedMessage =
        rbelLogger
            .getRbelConverter()
            .parseMessage(curlMessage.getBytes(), new RbelMessageMetadata());

    assertThat(convertedMessage.findRbelPathMembers("$..*")).hasSizeGreaterThan(30);
    assertThat(rbelLogger.getMessageHistory()).isEmpty();
  }

  @Test
  void triggerBufferOverflow_shouldRemoveOldestMessages() {
    final String curlMessage = RandomStringUtils.insecure().nextAlphanumeric(5000);
    final RbelLogger rbelLogger =
        RbelLogger.build(
            RbelConfiguration.builder().manageBuffer(true).rbelBufferSizeInMb(1).build());
    RbelConverter rbelConverter = rbelLogger.getRbelConverter();

    var allParsedMessages = new LinkedList<RbelElement>();
    final int maxBufferSizeInBytes = MB;
    for (int i = 0; i < maxBufferSizeInBytes / curlMessage.getBytes().length + 1; i++) {
      allParsedMessages.add(
          rbelConverter.parseMessage(curlMessage.getBytes(), new RbelMessageMetadata()));
    }

    var rbelLoggerHistory = rbelLogger.getMessageHistory();
    final int sizeOfMessagesInRbelLogger =
        (int) rbelLoggerHistory.stream().mapToLong(RbelElement::getSize).sum();
    assertThat(maxBufferSizeInBytes).isGreaterThan(sizeOfMessagesInRbelLogger);
    assertThat(allParsedMessages.size()).isGreaterThan(rbelLoggerHistory.size());
    assertThat(rbelLoggerHistory)
        .containsExactlyElementsOf(
            allParsedMessages.subList(
                allParsedMessages.size() - rbelLoggerHistory.size(), allParsedMessages.size()));
  }

  @Test
  void negativeBufferSize_shouldClearAllMessages() {
    final String curlMessage = RandomStringUtils.insecure().nextAlphanumeric(5000);
    final RbelLogger rbelLogger =
        RbelLogger.build(
            RbelConfiguration.builder().manageBuffer(true).rbelBufferSizeInMb(-1).build());
    RbelConverter rbelConverter = rbelLogger.getRbelConverter();

    rbelConverter.parseMessage(curlMessage.getBytes(), new RbelMessageMetadata());
    assertThat(rbelLogger.getMessageHistory()).isEmpty();
  }

  @Test
  void repeatedBufferOverflow_shouldRemoveOldestMessages() {
    final String curlMessage = RandomStringUtils.insecure().nextAlphanumeric(5000);
    final RbelLogger rbelLogger =
        RbelLogger.build(
            RbelConfiguration.builder().manageBuffer(true).rbelBufferSizeInMb(1).build());
    RbelConverter rbelConverter = rbelLogger.getRbelConverter();

    final int messageSize = curlMessage.getBytes().length;
    final int maxBufferSize = MB;
    final int messagesForOverflow = (maxBufferSize / messageSize) + 5; // +5 für sicheren Overflow

    List<RbelElement> allMessages = new ArrayList<>();

    for (int i = 0; i < messagesForOverflow; i++) {
      RbelElement message =
          rbelConverter.parseMessage(curlMessage.getBytes(), new RbelMessageMetadata());
      allMessages.add(message);
    }

    List<RbelElement> historyMessages = new ArrayList<>(rbelLogger.getMessageHistory());

    assertThat(historyMessages.size()).isLessThan(allMessages.size());
    assertThat(historyMessages).isNotEmpty();

    assertThat(historyMessages)
        .containsExactlyElementsOf(
            allMessages.subList(allMessages.size() - historyMessages.size(), allMessages.size()));

    long totalSize = historyMessages.stream().mapToLong(RbelElement::getSize).sum();
    assertThat(totalSize).isLessThanOrEqualTo(maxBufferSize);
  }

  @Test
  void bufferManagementDisabled_shouldNotRemoveMessages() {
    final int bufferSizeInBytes = MB;

    final int messageSizeToTriggerOverflow = (bufferSizeInBytes / 5) + 1;
    final String curlMessage =
        RandomStringUtils.insecure().nextAlphanumeric(messageSizeToTriggerOverflow);

    final RbelLogger rbelLogger =
        RbelLogger.build(
            RbelConfiguration.builder().manageBuffer(false).rbelBufferSizeInMb(1).build());
    RbelConverter rbelConverter = rbelLogger.getRbelConverter();

    for (int i = 0; i < 10; i++) {
      rbelConverter.parseMessage(curlMessage.getBytes(), new RbelMessageMetadata());
    }

    assertThat(rbelLogger.getMessageHistory()).hasSize(10);

    long totalSize = rbelLogger.getMessageHistory().stream().mapToLong(RbelElement::getSize).sum();
    assertThat(totalSize).isGreaterThan(bufferSizeInBytes);
  }
}
