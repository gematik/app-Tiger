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

import static de.gematik.test.tiger.mockserver.model.Header.header;

import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import java.net.URI;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/** Callback used for all Forward-Proxy routes in the TigerProxy. */
@Slf4j
public class ForwardProxyCallback extends AbstractRouteProxyCallback {

  public ForwardProxyCallback(TigerProxy tigerProxy, TigerProxyRoute tigerRoute) {
    super(tigerProxy, tigerRoute);
    tigerProxy.addAlternativeName(getSourceUri().getHost());
  }

  @SneakyThrows
  @Override
  @SuppressWarnings("java:S1075")
  public HttpRequest handleRequest(HttpRequest req) {
    applyModifications(req);
    req.replaceHeader(header("Host", getTargetUrl().getHost() + ":" + getPort()));
    if (getTigerRoute().getBasicAuth() != null) {
      req.replaceHeader(
          header("Authorization", getTigerRoute().getBasicAuth().toAuthorizationHeaderValue()));
    }
    String getTargetUrl = getTargetUrl().getPath();
    if (getTargetUrl.endsWith("/")) {
      getTargetUrl = getTargetUrl.substring(0, getTargetUrl.length() - 1);
    }
    String requestPath = stripRoutePattern(req.getPath());
    if (requestPath.equals("/") && !isAddTrailingSlash()) {
      requestPath = "";
    }
    final String path = getTargetUrl + requestPath;
    return cloneRequest(req)
        .setPath(path)
        .setSecure(getTigerRoute().getTo().startsWith("https://"))
        .setQueryStringParameters(req.getQueryStringParameters());
  }

  @SneakyThrows
  private String stripRoutePattern(String requestUri) {
    final URI routeFromUri = new URI(getTigerRoute().getFrom());
    log.atInfo()
        .addArgument(requestUri)
        .addArgument(routeFromUri::getPath)
        .log("Stripping route pattern from request path: {} will delete {}");
    return requestUri.substring(routeFromUri.getPath().length());
  }

  @Override
  protected String extractProtocolAndHostForRequest(HttpRequest request) {
    return getSourceUri().getScheme() + "://" + getSourceUri().getHost();
  }

  @Override
  protected String printTrafficTarget(HttpRequest req) {
    return getTigerRoute().getTo();
  }
}
