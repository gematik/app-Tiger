/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.mock.listeners;

import de.gematik.test.tiger.mockserver.mock.RequestMatchers;

/*
 * @author jamesdbloom
 */
public interface MockServerMatcherListener {

  void updated(RequestMatchers requestMatchers, MockServerMatcherNotifier.Cause cause);
}
