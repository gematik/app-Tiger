/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.client;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TigerExceptionDto {

    private final String message;
    private final String stacktrace;
    private final String className;
}
