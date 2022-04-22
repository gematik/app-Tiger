/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.env;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TigerStatusUpdate {

    private Map<String, FeatureUpdate> featureMap;
    private Map<String, TigerServerStatusUpdate> serverUpdate;
}
