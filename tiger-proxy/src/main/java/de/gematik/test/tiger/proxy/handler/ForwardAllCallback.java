/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.proxy.handler;

import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;

import de.gematik.test.tiger.proxy.TigerProxy;
import org.mockserver.model.HttpRequest;

public class ForwardAllCallback extends AbstractTigerRouteCallback {

  public ForwardAllCallback(TigerProxy tigerProxy) {
    super(tigerProxy, null);
  }

  @Override
  protected HttpRequest handleRequest(HttpRequest req) {
    return forwardOverriddenRequest(
            req.withSocketAddress(
                req.isSecure(),
                req.socketAddressFromHostHeader().getHostName(),
                req.socketAddressFromHostHeader().getPort()))
        .getRequestOverride();
  }

  @Override
  protected String extractProtocolAndHostForRequest(HttpRequest request) {
    return request.getSocketAddress().getScheme()
        + "://"
        + request.getSocketAddress().getHost()
        + ":"
        + request.getSocketAddress().getPort();
  }

  @Override
  boolean shouldLogTraffic() {
    return true;
  }
}
