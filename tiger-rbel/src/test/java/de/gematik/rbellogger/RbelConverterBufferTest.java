/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.Optional;
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
            .parseMessage(curlMessage.getBytes(), null, null, Optional.of(ZonedDateTime.now()));

    assertThat(convertedMessage.findRbelPathMembers("$..*")).hasSizeGreaterThan(30);
    assertThat(rbelLogger.getMessageHistory()).isEmpty();
  }

  @Test
  void triggerBufferOverflow_shouldRemoveOldestMessages() {
    final String curlMessage = RandomStringUtils.randomAlphanumeric(5000);
    final RbelLogger rbelLogger =
        RbelLogger.build(
            RbelConfiguration.builder().manageBuffer(true).rbelBufferSizeInMb(1).build());
    RbelConverter rbelConverter = rbelLogger.getRbelConverter();

    var allParsedMessages = new LinkedList<RbelElement>();
    final int maxBufferSizeInBytes = 1024 * 1024;
    for (int i = 0; i < maxBufferSizeInBytes / curlMessage.getBytes().length + 1; i++) {
      allParsedMessages.add(
          rbelConverter.parseMessage(
              curlMessage.getBytes(), null, null, Optional.of(ZonedDateTime.now())));
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
