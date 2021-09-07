/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.data;

import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import lombok.*;

@RequiredArgsConstructor
@Builder(toBuilder = true)
@Data
public class TigerRoute {

    @With
    private final String id;
    private final String from;
    private final String to;
    private final boolean internalRoute;
    private final boolean disableRbelLogging;
    private final TigerBasicAuthConfiguration basicAuth;
}
