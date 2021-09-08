/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.data;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Data
public class TigerRoute {

    @With
    private String id;
    private String from;
    private String to;
    private boolean internalRoute;
    private boolean disableRbelLogging;
    private TigerBasicAuthConfiguration basicAuth;
}
