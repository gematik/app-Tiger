/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.handler;

import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.proxy.TigerProxy;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.model.HttpRequest;

@Slf4j
@EqualsAndHashCode(callSuper = true)
public class ReverseProxyCallback extends AbstractTigerRouteCallback {

    private static final String HTTPS_PREFIX = "https://";
    private final URI targetUri;
    private final int port;

    @SneakyThrows(URISyntaxException.class)
    public ReverseProxyCallback(TigerProxy tigerProxy, TigerRoute route) {
        super(tigerProxy, route);
        this.targetUri = new URI(route.getTo());
        if (targetUri.getPort() < 0) {
            port = route.getTo().startsWith(HTTPS_PREFIX) ? 443 : 80;
        } else {
            port = targetUri.getPort();
        }
    }

    @Override
    public HttpRequest handleRequest(HttpRequest httpRequest) {
        applyModifications(httpRequest);
        final HttpRequest request = cloneRequest(httpRequest)
            .withSocketAddress(
                getTigerRoute().getTo().startsWith(HTTPS_PREFIX),
                targetUri.getHost(),
                port
            )
            .withSecure(getTigerRoute().getTo().startsWith(HTTPS_PREFIX))
            .withPath(patchPath(httpRequest.getPath().getValue()));

        if (getTigerProxy().getTigerProxyConfiguration().isRewriteHostHeader()) {
            request
                .removeHeader("Host")
                .withHeader("Host", targetUri.getHost() + ":" + port);
        }
        if (getTigerRoute().getBasicAuth() != null) {
            request.withHeader("Authorization", getTigerRoute().getBasicAuth().toAuthorizationHeaderValue());
        }

        return request;
    }

    private String patchPath(String requestPath) {
        String patchedUrl = requestPath.replaceFirst(targetUri.toString(), "");
        if (!getTigerRoute().getFrom().equals("/")) {
            patchedUrl = patchedUrl.substring(getTigerRoute().getFrom().length());
        }
        if (patchedUrl.startsWith("/")) {
            return targetUri.getPath() + patchedUrl;
        } else {
            return targetUri.getPath() + "/" + patchedUrl;
        }
    }

    @Override
    protected String extractProtocolAndHostForRequest(HttpRequest request) {
        return getTigerRoute().getTo();
    }
}
