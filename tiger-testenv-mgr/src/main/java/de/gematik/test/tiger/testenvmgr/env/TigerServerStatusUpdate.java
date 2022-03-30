/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.env;

import de.gematik.test.tiger.common.config.ServerType;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TigerServerStatusUpdate {

    private TigerServerStatus status;
    private String statusMessage;
    private ServerType type;
    private String baseUrl;
}
