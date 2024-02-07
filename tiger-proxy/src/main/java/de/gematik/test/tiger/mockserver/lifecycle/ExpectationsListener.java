/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.lifecycle;

import de.gematik.test.tiger.mockserver.mock.Expectation;
import java.util.List;

/*
 * @author jamesdbloom
 */
public interface ExpectationsListener {

  void updated(List<Expectation> expectations);
}
