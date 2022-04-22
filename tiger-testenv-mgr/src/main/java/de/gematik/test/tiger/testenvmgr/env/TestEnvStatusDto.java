/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.env;

import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

@RequiredArgsConstructor
@Builder
@Data
public class TestEnvStatusDto {

    private final Map<String, FeatureUpdate> featureMap;
    private final Map<String, TigerServerStatusUpdateDto> servers;

    public static TestEnvStatusDto createFrom(final TigerStatusUpdate update) {
        return TestEnvStatusDto.builder()
                .featureMap(update.getFeatureMap())
                .servers(mapServer(update.getServerUpdate()))
                .build();
    }

    private static Map<String, TigerServerStatusUpdateDto> mapServer(
        final Map<String, TigerServerStatusUpdate> serverUpdate) {
        if (serverUpdate == null) {
            return null;
        }
        return serverUpdate.entrySet().stream()
            .map(entry -> Pair.of(
                entry.getKey(),
                TigerServerStatusUpdateDto.fromUpdate(entry.getValue())
            ))
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }
}
