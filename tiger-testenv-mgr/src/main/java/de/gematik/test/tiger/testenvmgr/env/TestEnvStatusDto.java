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

    private final MessageUpdateDto message;
    private final Map<String, TigerServerStatusUpdateDto> servers;

    public static TestEnvStatusDto createFrom(final TigerStatusUpdate update) {
        return TestEnvStatusDto.builder()
            .message(MessageUpdateDto.builder()
                .text(update.getStatusMessage())
                .build())
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

    @Builder
    @Data
    @RequiredArgsConstructor
    public static class MessageUpdateDto {

        private final String text;
    }
}
