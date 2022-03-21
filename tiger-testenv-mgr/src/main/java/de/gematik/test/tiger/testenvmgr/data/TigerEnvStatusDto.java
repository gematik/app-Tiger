/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.data;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;

@Data
public class TigerEnvStatusDto {

    private String currentStatusMessage = "";
    private Map<String, TigerServerStatusDto> servers = new HashMap<>();
}
