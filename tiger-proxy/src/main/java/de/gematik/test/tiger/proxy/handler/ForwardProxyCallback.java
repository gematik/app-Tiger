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

import static org.mockserver.model.Header.header;

import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.proxy.TigerProxy;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.model.HttpRequest;

@Slf4j
public class ForwardProxyCallback extends AbstractRouteProxyCallback {

  public ForwardProxyCallback(TigerProxy tigerProxy, TigerRoute tigerRoute) {
    super(tigerProxy, tigerRoute);
    tigerProxy.addAlternativeName(getSourceUri().getHost());
  }

  @Override
  public HttpRequest handleRequest(HttpRequest req) {
    applyModifications(req);
    req.replaceHeader(header("Host", getTargetUrl().getHost() + ":" + getPort()));
    if (getTigerRoute().getBasicAuth() != null) {
      req.replaceHeader(
          header("Authorization", getTigerRoute().getBasicAuth().toAuthorizationHeaderValue()));
    }
    final String path =
        req.getPath().toString().equals("/")
            ? getTargetUrl().getPath() + "/"
            : getTargetUrl().getPath() + req.getPath();
    return cloneRequest(req)
        .withPath(path)
        .withSecure(getTigerRoute().getTo().startsWith("https://"))
        .withQueryStringParameters(req.getQueryStringParameters());
  }

  @Override
  protected String extractProtocolAndHostForRequest(HttpRequest request) {
    return getSourceUri().getScheme() + "://" + getSourceUri().getHost();
  }
}
