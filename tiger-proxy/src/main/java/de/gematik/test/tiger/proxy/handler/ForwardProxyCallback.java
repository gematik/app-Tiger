/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.handler;

import static org.mockserver.model.Header.header;

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerRoute;
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
  @SuppressWarnings("java:S1075")
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
