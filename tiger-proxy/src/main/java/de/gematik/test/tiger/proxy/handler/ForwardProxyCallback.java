/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.handler;

import static de.gematik.test.tiger.mockserver.model.Header.header;

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerRoute;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.proxy.TigerProxy;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/** Callback used for all Forward-Proxy routes in the TigerProxy. */
@Slf4j
public class ForwardProxyCallback extends AbstractRouteProxyCallback {

  public ForwardProxyCallback(TigerProxy tigerProxy, TigerRoute tigerRoute) {
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
    String patchedPath = getTargetUrl().getPath();
    if (patchedPath.endsWith("/")) {
      patchedPath = patchedPath.substring(0, patchedPath.length() - 1);
    }
    String requestPath = req.getPath();
    if (requestPath.equals("/") && !isAddTrailingSlash()) {
      requestPath = "";
    }
    final String path = patchedPath + requestPath;
    return cloneRequest(req)
        .withPath(path)
        .withSecure(getTigerRoute().getTo().startsWith("https://"))
        .withQueryStringParameters(req.getQueryStringParameters());
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
