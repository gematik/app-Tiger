/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;

public interface RbelConverterPlugin {

    void consumeElement(RbelElement rbelElement, RbelConverter converter);

    default boolean ignoreOversize() {
        return false;
    }
}
