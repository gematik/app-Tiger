/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.stop;

import java.io.Closeable;

/*
 * @author jamesdbloom
 */
public interface Stoppable extends Closeable {

  void stop();
}
