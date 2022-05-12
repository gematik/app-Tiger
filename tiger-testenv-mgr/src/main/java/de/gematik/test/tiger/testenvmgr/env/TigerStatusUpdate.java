/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.env;

import java.util.LinkedHashMap;
import java.util.Objects;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TigerStatusUpdate {
    static long lastIndex;
    static Object indexMutex = new Object();


    public TigerStatusUpdate(long dummy, LinkedHashMap<String, FeatureUpdate> featureMap, LinkedHashMap<String, TigerServerStatusUpdate> serverUpdate, String bannerMessage, String bannerColor) {
        this.featureMap = Objects.requireNonNullElseGet(featureMap, LinkedHashMap::new);
        this.serverUpdate = Objects.requireNonNullElseGet(serverUpdate, LinkedHashMap::new);
        this.bannerMessage = bannerMessage;
        this.bannerColor = bannerColor;
        synchronized (indexMutex) {
            index = lastIndex++;
        }
    }

    private long index;
    private LinkedHashMap<String, FeatureUpdate> featureMap;
    private LinkedHashMap<String, TigerServerStatusUpdate> serverUpdate;

    private String bannerMessage;
    private String bannerColor;
}
