/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.*;

@Data
@AllArgsConstructor(onConstructor_ = @JsonIgnore)
@NoArgsConstructor
@Builder
@ToString
@JsonInclude(Include.NON_NULL)
public class TigerLibConfig {

    @Builder.Default
    public boolean activateMonitorUI = false;
    @Builder.Default
    private boolean rbelPathDebugging = false;
    @Builder.Default
    private boolean rbelAnsiColors = true;
    @Builder.Default
    private boolean addCurlCommandsForRaCallsToReport = true;
}
