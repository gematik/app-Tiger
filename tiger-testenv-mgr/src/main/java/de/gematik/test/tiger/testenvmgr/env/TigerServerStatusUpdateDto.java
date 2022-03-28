/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.env;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Builder
@RequiredArgsConstructor
@AllArgsConstructor
@Data
public class TigerServerStatusUpdateDto {

    private String statusMessage;

    public static TigerServerStatusUpdateDto fromUpdate(final TigerServerStatusUpdate value) {
        return TigerServerStatusUpdateDto.builder()
            .statusMessage(value.getStatusMessage())
            .build();
    }
}
