/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util;

import de.gematik.rbellogger.data.RbelElement;
import java.util.Deque;

public interface RbelMessagesSupplier {
  void addRbelMessageListener(IRbelMessageListener listener);

  Deque<RbelElement> getRbelMessages();
}
