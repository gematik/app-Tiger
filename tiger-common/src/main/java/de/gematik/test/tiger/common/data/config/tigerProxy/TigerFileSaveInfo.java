/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.data.config.tigerProxy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

@Data
@AllArgsConstructor(onConstructor_ = @JsonIgnore)
@NoArgsConstructor
@Builder
public class TigerFileSaveInfo {

    private String sourceFile;

    @Builder.Default
    private boolean writeToFile = false;
    @Builder.Default
    private String filename = "tiger-proxy.tgr";
    @Builder.Default
    private boolean clearFileOnBoot = false;
}
