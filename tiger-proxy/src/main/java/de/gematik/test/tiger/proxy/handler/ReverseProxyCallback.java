/*
 *
 * Copyright 2021-2025 gematik GmbH
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

import static de.gematik.test.tiger.mockserver.model.Header.header;

import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/** Callback used for all Reverse-Proxy routes in the TigerProxy. */
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class ReverseProxyCallback extends AbstractRouteProxyCallback {

  public ReverseProxyCallback(TigerProxy tigerProxy, TigerProxyRoute route) {
    super(tigerProxy, route);
  }

  @Override
  public HttpRequest handleRequest(HttpRequest httpRequest) {
    applyModifications(httpRequest);
    final HttpRequest request =
        cloneRequest(httpRequest)
            .setReceiverAddress(
                getTargetUrl().getProtocol().equals("https"), getTargetUrl().getHost(), getPort())
            .setSecure(getTigerRoute().getTo().startsWith("https"))
            .setPath(patchPath(httpRequest.getPath()));

    if (getTigerProxy().getTigerProxyConfiguration().isRewriteHostHeader()) {
      request.removeHeader("Host").withHeader("Host", getTargetUrl().getHost() + ":" + getPort());
    }
    if (getTigerRoute().getAuthentication() != null) {
      getTigerRoute()
          .getAuthentication()
          .toAuthorizationHeaderValue()
          .ifPresent(auth -> request.replaceHeader(header("Authorization", auth)));
    }

    return request;
  }

  @Override
  protected String rewriteConcreteLocation(String originalLocation) {
    try {
      final URI newUri = new URI(getTargetUrl().getPath()).relativize(new URI(originalLocation));
      if (newUri.isAbsolute()) {
        return newUri.toString();
      } else {
        return "/" + newUri;
      }
    } catch (URISyntaxException e) {
      return originalLocation;
    }
  }

  @Override
  protected String extractProtocolAndHostForRequest(HttpRequest request) {
    return getTigerRoute().getTo();
  }

  @Override
  protected String printTrafficTarget(HttpRequest req) {
    return getTigerRoute().getTo();
  }
}
