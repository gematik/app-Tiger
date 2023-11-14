/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.modifier;

import de.gematik.rbellogger.data.RbelElement;

public interface RbelElementWriter {
  boolean canWrite(RbelElement oldTargetElement);

  byte[] write(RbelElement oldTargetElement, RbelElement oldTargetModifiedChild, byte[] newContent);
}
