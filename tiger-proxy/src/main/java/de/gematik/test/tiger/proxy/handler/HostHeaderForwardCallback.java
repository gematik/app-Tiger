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

import static de.gematik.test.tiger.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;

import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.proxy.TigerProxy;
import lombok.extern.slf4j.Slf4j;

/**
 * Fallback callback for reverse-proxy requests that do not match any configured route. When {@code
 * honorHostHeaderRouting} is enabled, this callback forwards the request to the host specified in
 * the incoming {@code Host} header.
 */
@Slf4j
public class HostHeaderForwardCallback extends AbstractFallbackRouteCallback {

  public HostHeaderForwardCallback(TigerProxy tigerProxy) {
    super(tigerProxy);
  }

  @Override
  public boolean matches(HttpRequest httpRequest) {
    return httpRequest.optionalSocketAddressFromHostHeader().isPresent();
  }

  @Override
  protected HttpRequest handleRequest(HttpRequest req) {
    return req.optionalSocketAddressFromHostHeader().stream()
        .peek(
            address ->
                log.atDebug()
                    .addArgument(address::getHostName)
                    .addArgument(address::getPort)
                    .log("Received unmatched reverse-proxy request with Host header {}:{}"))
        .map(
            address ->
                forwardOverriddenRequest(
                        req.setReceiverAddress(
                            req.isSecure(), address.getHostName(), address.getPort()))
                    .getRequestOverride())
        .findFirst()
        .orElse(req);
  }

  @Override
  protected String printTrafficTarget(HttpRequest req) {
    return req.optionalSocketAddressFromHostHeader()
        .map(address -> address.getHostString() + ":" + address.getPort())
        .orElse("<unknown - no Host header>");
  }
}
