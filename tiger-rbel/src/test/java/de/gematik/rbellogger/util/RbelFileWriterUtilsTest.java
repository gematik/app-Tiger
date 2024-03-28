/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.file.RbelFileWriter;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

class RbelFileWriterUtilsTest {

  @Test
  void readFileTwice_shouldOnlyReadMsgsOnceBasedOnUuid() throws IOException {
    RbelLogger rbelLogger = RbelLogger.build(new RbelConfiguration().setActivateAsn1Parsing(false));
    var rbelFileWriter = new RbelFileWriter(rbelLogger.getRbelConverter());

    String rawSavedVauMessages =
        FileUtils.readFileToString(new File("src/test/resources/trafficLog.tgr"));
    rbelFileWriter.convertFromRbelFile(rawSavedVauMessages);

    int initialNumberOfMessage = rbelLogger.getMessageHistory().size();
    rbelFileWriter.convertFromRbelFile(rawSavedVauMessages);

    assertThat(rbelLogger.getMessageHistory()).hasSize(initialNumberOfMessage);
  }
}
