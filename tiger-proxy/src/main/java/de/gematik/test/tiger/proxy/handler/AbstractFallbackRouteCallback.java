/*
 *
 * Copyright 2021-2026 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.proxy.handler;

import de.gematik.rbellogger.util.RbelSocketAddress;
import de.gematik.test.tiger.mockserver.model.Action;
import de.gematik.test.tiger.mockserver.model.CloseChannel;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.SocketAddress;
import de.gematik.test.tiger.proxy.TigerProxy;
import java.util.Optional;
import lombok.val;

/**
 * Abstract base class for fallback callbacks ({@link ForwardAllCallback} and {@link
 * HostHeaderForwardCallback}). These callbacks handle requests that do not match any configured
 * route. They share self-referencing loop detection: if the {@code Host} header points back at the
 * TigerProxy itself, the connection is closed to prevent infinite loops.
 */
public abstract class AbstractFallbackRouteCallback extends AbstractTigerRouteCallback {

  protected AbstractFallbackRouteCallback(TigerProxy tigerProxy) {
    super(tigerProxy, null);
  }

  @Override
  protected RbelSocketAddress extractReceiverAddressForRequest(HttpRequest request) {
    return Optional.ofNullable(request.getReceiverAddress())
        .map(SocketAddress::toRbelSocketAddress)
        .orElse(null);
  }

  @Override
  boolean shouldLogTraffic() {
    return true;
  }

  @Override
  public Optional<Action> cannedResponse(HttpRequest httpRequest) {
    if (targetsSelf(httpRequest)) {
      return Optional.of(new CloseChannel());
    } else {
      return Optional.empty();
    }
  }

  private boolean targetsSelf(HttpRequest req) {
    return req.optionalSocketAddressFromHostHeader()
        .map(
            address -> {
              val requestPort = address.getPort();
              val requestHostName = address.getHostString();
              return requestPort == getTigerProxy().getProxyPort()
                  && (requestHostName.equalsIgnoreCase("127.0.0.1")
                      || requestHostName.equalsIgnoreCase("localhost"));
            })
        .orElse(false);
  }
}
