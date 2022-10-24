/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class RbelFileWriterUtilsTest {

    @Test
    public void readFileTwice_shouldOnlyReadMsgsOnceBasedOnUuid() throws IOException {
        RbelLogger rbelLogger = RbelLogger.build(new RbelConfiguration()
            .setActivateAsn1Parsing(false));

        String rawSavedVauMessages = FileUtils.readFileToString(new File("src/test/resources/trafficLog.tgr"));
        RbelFileWriterUtils.convertFromRbelFile(rawSavedVauMessages, rbelLogger.getRbelConverter());

        int initialNumberOfMessage = rbelLogger.getMessageHistory().size();
        RbelFileWriterUtils.convertFromRbelFile(rawSavedVauMessages, rbelLogger.getRbelConverter());

        assertThat(rbelLogger.getMessageHistory().size())
            .isEqualTo(initialNumberOfMessage);
    }
}