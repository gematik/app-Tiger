/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.handler;

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerRoute;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.proxy.TigerProxy;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/** Callback used for all Reverse-Proxy routes in the TigerProxy. */
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class ReverseProxyCallback extends AbstractRouteProxyCallback {

  public ReverseProxyCallback(TigerProxy tigerProxy, TigerRoute route) {
    super(tigerProxy, route);
  }

  @Override
  public HttpRequest handleRequest(HttpRequest httpRequest) {
    applyModifications(httpRequest);
    final HttpRequest request =
        cloneRequest(httpRequest)
            .withSocketAddress(
                getTargetUrl().getProtocol().equals("https"), getTargetUrl().getHost(), getPort())
            .withSecure(getTigerRoute().getTo().startsWith("https"))
            .withPath(patchPath(httpRequest.getPath()));

    if (getTigerProxy().getTigerProxyConfiguration().isRewriteHostHeader()) {
      request.removeHeader("Host").withHeader("Host", getTargetUrl().getHost() + ":" + getPort());
    }
    if (getTigerRoute().getBasicAuth() != null) {
      request.withHeader(
          "Authorization", getTigerRoute().getBasicAuth().toAuthorizationHeaderValue());
    }

    return request;
  }

  private String patchPath(String requestPath) {
    String patchedUrl = requestPath.replaceFirst(getTargetUrl().toString(), "");
    if (!getTigerRoute().getFrom().equals("/")) {
      patchedUrl = patchedUrl.substring(getTigerRoute().getFrom().length());
    }
    if (patchedUrl.startsWith("/")) {
      if (isAddTrailingSlash()
          && !patchedUrl.endsWith("/")
          && (requestPath.equals("/") || requestPath.isEmpty())) {
        return getTargetUrl().getPath() + patchedUrl + "/";
      } else {
        return getTargetUrl().getPath() + patchedUrl;
      }
    } else {
      return getTargetUrl().getPath() + "/" + patchedUrl;
    }
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
