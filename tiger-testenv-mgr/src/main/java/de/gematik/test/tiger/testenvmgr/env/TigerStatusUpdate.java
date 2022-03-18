/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.env;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TigerStatusUpdate {

    private String statusMessage;
}
