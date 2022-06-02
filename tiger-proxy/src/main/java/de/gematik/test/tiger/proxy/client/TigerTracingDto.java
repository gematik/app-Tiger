/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.client;

import de.gematik.rbellogger.data.RbelHostname;
import java.time.ZonedDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TigerTracingDto {

    private final String requestUuid;
    private final String responseUuid;
    private final RbelHostname sender;
    private final RbelHostname receiver;
    private final ZonedDateTime requestTransmissionTime;
    private final ZonedDateTime responseTransmissionTime;
}
