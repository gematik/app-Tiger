/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.testenvmgr.env;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

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

    public TestEnvStatusDto(long index, LinkedHashMap<String, FeatureUpdate> featureMap, LinkedHashMap<String, TigerServerStatusUpdateDto> servers,
        String bannerMessage, String bannerColor) {
        this.index = index;
        this.featureMap = featureMap;
        this.servers = servers;
        this.bannerMessage = bannerMessage;
        this.bannerColor = bannerColor;
    }

    public static TestEnvStatusDto createFrom(final TigerStatusUpdate update) {
        return TestEnvStatusDto.builder()
            .featureMap(update.getFeatureMap())
            .servers(mapServer(update.getServerUpdate()))
            .bannerMessage(update.getBannerMessage())
            .bannerColor(update.getBannerColor())
            .index(update.getIndex())
            .build();
    }

    private static LinkedHashMap<String, TigerServerStatusUpdateDto> mapServer(
        final LinkedHashMap<String, TigerServerStatusUpdate> serverUpdate) {
        if (serverUpdate == null) {
            return null;
        }
        LinkedHashMap updatedMap = new LinkedHashMap();
        serverUpdate.forEach((key, serverStatusUpdate) -> {
            updatedMap.put(key, TigerServerStatusUpdateDto.fromUpdate(serverStatusUpdate));
        });
        return updatedMap;
    }
}
