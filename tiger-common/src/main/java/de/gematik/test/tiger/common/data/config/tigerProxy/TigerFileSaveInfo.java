/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.data.config.tigerProxy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.gematik.rbellogger.configuration.RbelFileSaveInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor(onConstructor_ = @JsonIgnore)
@NoArgsConstructor
@Builder
public class TigerFileSaveInfo {

    private boolean writeToFile;
    private String sourceFile;
    private String filename;
    private boolean clearFileOnBoot;

    public RbelFileSaveInfo toRbelFileSaveInfo() {
        return RbelFileSaveInfo.builder()
            .writeToFile(writeToFile)
            .filename(filename)
            .clearFileOnBoot(clearFileOnBoot)
            .build();
    }
}
