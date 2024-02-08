/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.mock.action;

import de.gematik.test.tiger.mockserver.model.HttpRequest;

/*
 * @author jamesdbloom
 */
public interface ExpectationForwardCallback extends ExpectationCallback<HttpRequest> {

  /**
   * Called for every request when expectation condition has been satisfied. The request that
   * satisfied the expectation condition is passed as the parameter and the return value is the
   * request that will be proxied.
   *
   * @param httpRequest the request that satisfied the expectation condition
   * @return the request that will be proxied
   */
  HttpRequest handle(HttpRequest httpRequest) throws Exception;
}
