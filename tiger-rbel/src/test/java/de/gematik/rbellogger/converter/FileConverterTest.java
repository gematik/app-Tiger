/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.configuration.RbelFileSaveInfo;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import java.io.File;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class FileConverterTest {

    private final String filename = "target/testFileOut.rbl";

    @Test
    @SneakyThrows
    void shouldCreateFile() {
        readPcapAndWriteFile(RbelFileSaveInfo.builder()
            .writeToFile(true)
            .clearFileOnBoot(true)
            .filename(filename)
            .build());

        assertThat(new File(filename))
            .exists();
    }

    @Test
    @SneakyThrows
    void readFileAfterCreation_shouldContainAllMessages() {
        final RbelLogger initialRbelLogger = readPcapAndWriteFile(RbelFileSaveInfo.builder()
            .writeToFile(true)
            .clearFileOnBoot(true)
            .filename(filename)
            .build());
        initialRbelLogger.getRbelCapturer().initialize();

        final RbelLogger rbelLogger = readRbelFile();
        rbelLogger.getRbelCapturer().initialize();

        assertThat(rbelLogger.getMessageHistory())
            .hasSameSizeAs(initialRbelLogger.getMessageHistory());

        assertThat(rbelLogger.getMessageHistory().getFirst()
            .getFacetOrFail(RbelTcpIpMessageFacet.class).getReceiver().getFacet(RbelHostnameFacet.class).get().getDomain().getRawStringContent())
            .isEqualTo(initialRbelLogger.getMessageHistory().getFirst()
                .getFacetOrFail(RbelTcpIpMessageFacet.class).getReceiver().getFacet(RbelHostnameFacet.class).get().getDomain().getRawStringContent());

        assertThat(rbelLogger.getMessageHistory().getFirst()
            .getFacetOrFail(RbelTcpIpMessageFacet.class).getSender().getFacet(RbelHostnameFacet.class).get().getDomain().getRawStringContent())
            .isEqualTo(initialRbelLogger.getMessageHistory().getFirst()
                .getFacetOrFail(RbelTcpIpMessageFacet.class).getSender().getFacet(RbelHostnameFacet.class).get().getDomain().getRawStringContent());
        assertThat(rbelLogger.getMessageHistory().getFirst()
            .getFacetOrFail(RbelTcpIpMessageFacet.class).getSequenceNumber())
            .isEqualTo(initialRbelLogger.getMessageHistory().getFirst()
                .getFacetOrFail(RbelTcpIpMessageFacet.class).getSequenceNumber());
    }

    private RbelLogger readPcapAndWriteFile(RbelFileSaveInfo fileSaveInfo) throws Exception {
        final RbelLogger rbelLogger = new RbelConfiguration()
            .addCapturer(RbelFileReaderCapturer.builder()
                .rbelFile("src/test/resources/ssoTokenFlow.tgr")
                .build())
            .withFileSaveInfo(fileSaveInfo)
            .constructRbelLogger();
        rbelLogger.getRbelCapturer().initialize();

        return rbelLogger;
    }

    private RbelLogger readRbelFile() throws Exception {
        final RbelLogger rbelLogger = new RbelConfiguration()
            .addCapturer(RbelFileReaderCapturer.builder()
                .rbelFile(filename)
                .build())
            .constructRbelLogger();
        rbelLogger.getRbelCapturer().close();
        return rbelLogger;
    }
}
