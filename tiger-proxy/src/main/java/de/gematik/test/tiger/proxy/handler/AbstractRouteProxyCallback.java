/*
 * Copyright (c) 2024 gematik GmbH
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

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerRoute;
import de.gematik.test.tiger.proxy.TigerProxy;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public abstract class AbstractRouteProxyCallback extends AbstractTigerRouteCallback {

  private final URL targetUrl;
  private final boolean addTrailingSlash;
  private final URI sourceUri;
  private final int port;

  @SneakyThrows({MalformedURLException.class, URISyntaxException.class})
  AbstractRouteProxyCallback(TigerProxy tigerProxy, TigerRoute tigerRoute) {
    super(tigerProxy, tigerRoute);
    if (tigerRoute.getTo().endsWith("/")) {
      targetUrl = new URL(tigerRoute.getTo().substring(0, tigerRoute.getTo().length() - 1));
      addTrailingSlash = true;
    } else {
      targetUrl = new URL(tigerRoute.getTo());
      addTrailingSlash = false;
    }
    sourceUri = new URI(tigerRoute.getFrom());
    if (targetUrl.getPort() < 0) {
      port = targetUrl.getProtocol().equals("https") ? 443 : 80;
    } else {
      port = targetUrl.getPort();
    }
    tigerProxy.addAlternativeName(sourceUri.getHost());
  }

  @Override
  protected String rewriteConcreteLocation(String originalLocation) {
    try {
      final URI newUri = new URI(this.targetUrl.getPath()).relativize(new URI(originalLocation));
      if (newUri.isAbsolute()) {
        return newUri.toString();
      } else {
        return "/" + newUri;
      }
    } catch (URISyntaxException e) {
      return originalLocation;
    }
  }
}
