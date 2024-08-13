/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.proxy.handler;

import static de.gematik.test.tiger.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;

import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.proxy.TigerProxy;
import lombok.extern.slf4j.Slf4j;

/**
 * Callback used for as a forward-all route in the TigerProxy. The messages received here are simply
 * forwarded to the intended host. No rewriting of host or path is being done. It essentially serves
 * as a fallback when no specialised route is found matching the request.
 */
@Slf4j
public class ForwardAllCallback extends AbstractTigerRouteCallback {

  public ForwardAllCallback(TigerProxy tigerProxy) {
    super(tigerProxy, null);
  }

  @Override
  protected HttpRequest handleRequest(HttpRequest req) {
    return forwardOverriddenRequest(
            req.setSocketAddress(
                req.isSecure(),
                req.socketAddressFromHostHeader().getHostName(),
                req.socketAddressFromHostHeader().getPort()))
        .getRequestOverride();
  }

  @Override
  protected String extractProtocolAndHostForRequest(HttpRequest request) {
    if (request.getSocketAddress() == null) {
      return null;
    } else {
      return request.getSocketAddress().getScheme()
          + "://"
          + request.getSocketAddress().getHost()
          + ":"
          + request.getSocketAddress().getPort();
    }
  }

  @Override
  protected String printTrafficTarget(HttpRequest req) {
    return req.socketAddressFromHostHeader().getHostString()
        + ":"
        + req.socketAddressFromHostHeader().getPort();
  }

  @Override
  boolean shouldLogTraffic() {
    return true;
  }
}
