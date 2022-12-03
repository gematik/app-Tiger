/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.data.config.tigerProxy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor(onConstructor_ = @JsonIgnore)
@NoArgsConstructor
@Builder
public class TigerFileSaveInfo {

    @Builder.Default
    private String sourceFile = "";
    @Builder.Default
    private boolean writeToFile = false;
    @Builder.Default
    private String filename = "tiger-proxy.tgr";
    @Builder.Default
    private boolean clearFileOnBoot = false;
    @Builder.Default
    private String readFilter = "";
}
