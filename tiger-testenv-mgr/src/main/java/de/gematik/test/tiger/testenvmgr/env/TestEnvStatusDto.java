/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.env;

import de.gematik.test.tiger.testenvmgr.data.BannerType;
import java.util.LinkedHashMap;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Data transfer object that is deserialized on client side by Typescript class with same name.
 */
@RequiredArgsConstructor
@Builder
@Data
public class TestEnvStatusDto {

    private long index;
    private final LinkedHashMap<String, FeatureUpdate> featureMap;
    private final LinkedHashMap<String, TigerServerStatusUpdateDto> servers;

    private String bannerMessage;
    private String bannerColor;

    private BannerType bannerType;

    public TestEnvStatusDto(long index, LinkedHashMap<String, FeatureUpdate> featureMap, LinkedHashMap<String, TigerServerStatusUpdateDto> servers,
        String bannerMessage, String bannerColor, BannerType bannerType) {
        this.index = index;
        this.featureMap = featureMap;
        this.servers = servers;
        this.bannerMessage = bannerMessage;
        this.bannerColor = bannerColor;
        this.bannerType = bannerType;
    }

    public static TestEnvStatusDto createFrom(final TigerStatusUpdate update) {
        return TestEnvStatusDto.builder()
            .featureMap(update.getFeatureMap())
            .servers(mapServer(update.getServerUpdate()))
            .bannerMessage(update.getBannerMessage())
            .bannerColor(update.getBannerColor())
            .bannerType(update.getBannerType())
            .index(update.getIndex())
            .build();
    }

    private static LinkedHashMap<String, TigerServerStatusUpdateDto> mapServer(
        final LinkedHashMap<String, TigerServerStatusUpdate> serverUpdate) {
        if (serverUpdate == null) {
            return null;
        }
        LinkedHashMap<String, TigerServerStatusUpdateDto> updatedMap = new LinkedHashMap<>();
        serverUpdate.forEach((key, serverStatusUpdate) -> updatedMap.put(key, TigerServerStatusUpdateDto.fromUpdate(serverStatusUpdate)));
        return updatedMap;
    }
}
