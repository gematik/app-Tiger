/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter.listener;

import de.gematik.rbellogger.configuration.RbelFileSaveInfo;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.converter.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.util.RbelFileWriter;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerFileSaveInfo;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.Data;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

@Data
public class RbelFileAppenderPlugin implements RbelConverterPlugin {

    private final TigerFileSaveInfo fileSaveInfo;
    private final RbelFileWriter rbelFileWriter;

    public RbelFileAppenderPlugin(TigerFileSaveInfo fileSaveInfo, RbelConverter rbelConverter) {
        this.fileSaveInfo = fileSaveInfo;
        this.rbelFileWriter = new RbelFileWriter(rbelConverter);
        if (fileSaveInfo.isWriteToFile()
            && StringUtils.isNotEmpty(fileSaveInfo.getFilename())
            && fileSaveInfo.isClearFileOnBoot()) {
            FileUtils.deleteQuietly(new File(fileSaveInfo.getFilename()));
        }
    }

    @Override
    public void consumeElement(RbelElement rbelElement, RbelConverter converter) {
        if (fileSaveInfo.isWriteToFile()
            && StringUtils.isNotEmpty(fileSaveInfo.getFilename())
            && rbelElement.hasFacet(RbelTcpIpMessageFacet.class)) {
            try {
                FileUtils.writeStringToFile(new File(fileSaveInfo.getFilename()),
                    rbelFileWriter.convertToRbelFileString(rbelElement), StandardCharsets.UTF_8, true);
            } catch (IOException e) {
                throw new RuntimeException("Exception while trying to save RbelElement to file '" + fileSaveInfo.getFilename() + "'!", e);
            }
        }
    }

}
