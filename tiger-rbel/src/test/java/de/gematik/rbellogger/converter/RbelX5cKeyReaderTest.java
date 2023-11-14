/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Files;
import de.gematik.rbellogger.RbelLogger;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RbelX5cKeyReaderTest {

  @Test
  public void multipleKeyIds_shouldFindCorrectOne() throws IOException {
    RbelLogger logger = RbelLogger.build();
    logger
        .getRbelConverter()
        .parseMessage(
            Files.toByteArray(
                Path.of("src/test/resources/sampleMessages/multipleKeyIds.curl").toFile()),
            null,
            null,
            Optional.of(ZonedDateTime.now()));

    assertThat(logger.getRbelKeyManager().findKeyByName("puk_idp_sig")).isPresent();
  }
}
