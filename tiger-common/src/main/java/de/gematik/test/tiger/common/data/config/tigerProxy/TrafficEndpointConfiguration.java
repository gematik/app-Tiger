/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.data.config.tigerProxy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class TrafficEndpointConfiguration {

    @Builder.Default
    private String name = "tigerProxy Tracing Point";
    @Builder.Default
    private String wsEndpoint = "/tracing";
    @Builder.Default
    private String stompTopic = "/traces";
}
