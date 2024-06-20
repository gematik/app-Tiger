/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util;

import de.gematik.rbellogger.data.RbelElement;

public interface IRbelMessageListener {

  void triggerNewReceivedMessage(RbelElement el);
}
