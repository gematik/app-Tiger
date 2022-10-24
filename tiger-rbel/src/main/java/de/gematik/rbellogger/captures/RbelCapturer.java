/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.captures;

import de.gematik.rbellogger.converter.RbelConverter;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public abstract class RbelCapturer implements AutoCloseable {

    private RbelConverter rbelConverter;

    public abstract RbelCapturer initialize();
}
