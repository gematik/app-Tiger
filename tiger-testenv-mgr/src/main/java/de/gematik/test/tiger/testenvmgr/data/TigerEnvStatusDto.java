/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.data;

import de.gematik.test.tiger.testenvmgr.env.FeatureUpdate;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;

@Data
public class TigerEnvStatusDto {

    private Map<String, FeatureUpdate> featureMap;
    private Map<String, TigerServerStatusDto> servers = new HashMap<>();
}
