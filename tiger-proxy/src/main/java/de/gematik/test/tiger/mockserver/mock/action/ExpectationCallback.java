/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.mock.action;

import de.gematik.test.tiger.mockserver.model.HttpMessage;
import de.gematik.test.tiger.mockserver.model.HttpRequest;

/*
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public interface ExpectationCallback<T extends HttpMessage> {

  /**
   * Called for every request when expectation condition has been satisfied. The request that
   * satisfied the expectation condition is passed as the parameter and the return value is the
   * request that will be proxied or returned.
   *
   * @param httpRequest the request that satisfied the expectation condition
   * @return the request that will be proxied or the response that will be returned
   */
  T handle(HttpRequest httpRequest) throws Exception;

  default boolean matches(HttpRequest httpRequest) {
    return true;
  }
}
