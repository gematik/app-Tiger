/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.rbel;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class RequestParameter {

    private final String path;
    private final String rbelPath;
    private final String value;
    private final boolean startFromLastRequest;
    private final boolean filterPreviousRequest;
}
