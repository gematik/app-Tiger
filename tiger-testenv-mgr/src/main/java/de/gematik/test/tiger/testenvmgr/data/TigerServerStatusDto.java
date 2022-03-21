/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.data;

import de.gematik.test.tiger.common.config.ServerType;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerStatus;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TigerServerStatusDto {

    private String name;
    private String hostname;
    private ServerType type;
    private TigerServerStatus status;
    private String statusMessage;
    private List<String> statusUpdates;
}
