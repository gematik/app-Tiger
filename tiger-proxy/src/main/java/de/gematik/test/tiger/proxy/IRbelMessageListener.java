/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.data.RbelElement;

public interface IRbelMessageListener {

    void triggerNewReceivedMessage(RbelElement el);
}
