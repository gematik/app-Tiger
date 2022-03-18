/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.env;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Builder
@Data
public class TestEnvStatusDto {

    private final MessageUpdateDto message;

    public static TestEnvStatusDto createFrom(TigerStatusUpdate update) {
        return TestEnvStatusDto.builder()
            .message(MessageUpdateDto.builder()
                .text(update.getStatusMessage())
                .build())
            .build();
    }

    @Builder
    @Data
    @RequiredArgsConstructor
    public static class MessageUpdateDto {

        private final String text;
    }
}
