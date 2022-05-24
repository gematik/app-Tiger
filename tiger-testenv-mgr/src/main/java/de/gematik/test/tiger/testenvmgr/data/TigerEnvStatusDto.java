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

    private long currentIndex;
    private Map<String, FeatureUpdate> featureMap = new HashMap<>();
    private Map<String, TigerServerStatusDto> servers = new HashMap<>();
    private String bannerMessage;
    private String bannerColor;
    private BannerType bannerType;

    private String localProxyWebUiUrl;
}
