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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

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
    if (!getTigerRoute().isPreserveHostHeader()) {
      req.replaceHeader(header("Host", getTargetUrl().getHost() + ":" + getPort()));
    }
    if (getTigerRoute().getAuthentication() != null) {
      getTigerRoute()
          .getAuthentication()
          .toAuthorizationHeaderValue()
          .ifPresent(auth -> req.replaceHeader(header("Authorization", auth)));
    }
    final String path = patchPath(req.getPath());
    return cloneRequest(req)
        .setReceiverAddress(
            getTargetUrl().getProtocol().equals("https"), getTargetUrl().getHost(), getPort())
        .setPath(path)
        .setSecure(getTigerRoute().getTo().startsWith("https://"))
        .setQueryStringParameters(req.getQueryStringParameters());
  }

  @Override
  protected String extractProtocolAndHostForRequest(HttpRequest request) {
    val builder = new StringBuilder();
    builder.append(getSourceUri().getScheme()).append("://").append(getSourceUri().getHost());
    if (getSourceUri().getPort() != -1) {
      builder.append(":").append(getSourceUri().getPort());
    }
    return builder.toString();
  }

  @Override
  protected String printTrafficTarget(HttpRequest req) {
    return getTigerRoute().getTo();
  }
}
