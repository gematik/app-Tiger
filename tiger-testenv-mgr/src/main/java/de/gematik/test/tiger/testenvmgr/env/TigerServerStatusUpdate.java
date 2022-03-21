/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.env;

import de.gematik.test.tiger.testenvmgr.servers.TigerServerStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TigerServerStatusUpdate {

    private TigerServerStatus status;
    private String statusMessage;
}
