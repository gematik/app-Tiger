/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.data.RbelMessage;

public interface IRbelMessageListener {

    void triggerNewReceivedMessage(RbelMessage el);
}
