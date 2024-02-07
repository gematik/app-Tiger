/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.mock.action.http;

import de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient;
import de.gematik.test.tiger.mockserver.logging.MockServerLogger;

/*
 * @author jamesdbloom
 */
public class HttpForwardActionHandler extends HttpForwardAction {

  public HttpForwardActionHandler(MockServerLogger logFormatter, NettyHttpClient httpClient) {
    super(logFormatter, httpClient);
  }
}
