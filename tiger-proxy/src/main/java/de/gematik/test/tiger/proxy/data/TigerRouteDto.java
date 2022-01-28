/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.data;

import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Builder
@AllArgsConstructor
@Data
public class TigerRouteDto {

    private String id;
    private String from;
    private String to;
    private boolean disableRbelLogging;

    public static TigerRouteDto from(TigerRoute route) {
        return TigerRouteDto.builder()
            .from(route.getFrom())
            .to(route.getTo())
            .id(route.getId())
            .disableRbelLogging(route.isDisableRbelLogging())
            .build();
    }
}
