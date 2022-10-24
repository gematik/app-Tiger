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
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RbelConverterBufferTest {

    @Test
    void emptyBuffer_shouldNotContainMessages() throws IOException {
        final String curlMessage = readCurlFromFileWithCorrectedLineBreaks
            ("src/test/resources/sampleMessages/jwtMessage.curl");

        final RbelLogger rbelLogger = RbelLogger.build(RbelConfiguration.builder()
            .manageBuffer(true)
            .rbelBufferSizeInMb(0)
            .build());
        final RbelElement convertedMessage = rbelLogger.getRbelConverter()
            .parseMessage(curlMessage.getBytes(), null, null, Optional.of(ZonedDateTime.now()));

        assertThat(convertedMessage.findRbelPathMembers("$..*"))
            .hasSizeGreaterThan(30);
        assertThat(rbelLogger.getMessageHistory())
            .isEmpty();
    }

    @Test
    void fullBuffer_shouldNotExceedBufferSize() throws IOException {
        final String curlMessage = readCurlFromFileWithCorrectedLineBreaks
            ("src/test/resources/sampleMessages/jsonMessage.curl");

        final RbelLogger rbelLogger = RbelLogger.build(RbelConfiguration.builder()
            .manageBuffer(true)
            .rbelBufferSizeInMb(1)
            .build());
        RbelConverter rbelConverter = rbelLogger.getRbelConverter();
        int i = 0;
        while (i < 1000) {
            rbelConverter.parseMessage(curlMessage.getBytes(), null, null, Optional.of(ZonedDateTime.now()));
            i++;
        }

        assertThat(1024 * 1024).isGreaterThan((int) rbelLogger.getMessageHistory().stream()
            .mapToLong(RbelElement::getSize)
            .sum());
    }
}
