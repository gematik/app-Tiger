/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.decorator;

import de.gematik.rbellogger.data.RbelElement;

/** Modifies metadata if the given RbelElement */
public interface MessageMetadataModifier {
  void modifyMetadata(RbelElement message);
}
