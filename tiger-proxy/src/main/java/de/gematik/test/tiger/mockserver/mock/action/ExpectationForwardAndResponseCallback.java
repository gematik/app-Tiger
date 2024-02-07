/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.mock.action;

import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.HttpResponse;

/*
 * @author jamesdbloom
 */
public interface ExpectationForwardAndResponseCallback extends ExpectationForwardCallback {

  /**
   * Called for every request when expectation condition has been satisfied. The request that
   * satisfied the expectation condition is passed as the parameter and the return value is the
   * request that will be proxied.
   *
   * @param httpRequest the request that satisfied the expectation condition
   * @return the request that will be proxied
   */
  default HttpRequest handle(HttpRequest httpRequest) {
    return httpRequest;
  }

  /**
   * Called for every response received from a proxied request, the return value is the returned by
   * MockServer.
   *
   * @param httpRequest the request that was proxied
   * @param httpResponse the response the MockServer will return
   * @return the request that will be proxied
   */
  HttpResponse handle(HttpRequest httpRequest, HttpResponse httpResponse);
}
