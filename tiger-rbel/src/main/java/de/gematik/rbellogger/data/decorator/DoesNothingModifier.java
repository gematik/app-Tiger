/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.decorator;

import de.gematik.rbellogger.data.RbelElement;
import lombok.NoArgsConstructor;

/**
 * A Modifier that does nothing. Useful when a certain modification should not be active based on
 * the configuration and we avoid having to check all the time for the configured value.
 */
@NoArgsConstructor
public class DoesNothingModifier implements MessageMetadataModifier {
  @Override
  public void modifyMetadata(RbelElement message) {
    // does nothing
  }
}
