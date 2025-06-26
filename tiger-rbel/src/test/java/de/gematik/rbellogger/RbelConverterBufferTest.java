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
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import java.io.IOException;
import java.util.LinkedList;
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
    final int maxBufferSizeInBytes = 1024 * 1024;
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
}
