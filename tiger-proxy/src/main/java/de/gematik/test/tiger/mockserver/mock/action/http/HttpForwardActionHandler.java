/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.mock.action.http;

import de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient;

/*
 * @author jamesdbloom
 */
public class HttpForwardActionHandler extends HttpForwardAction {

  public HttpForwardActionHandler(NettyHttpClient httpClient) {
    super(httpClient);
  }
}
