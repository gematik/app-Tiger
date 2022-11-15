/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.handler;

import static org.mockserver.model.Header.header;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.proxy.TigerProxy;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.model.HttpRequest;

@Slf4j
public class ForwardProxyCallback extends AbstractTigerRouteCallback {

    private final URI targetUri;
    private final int port;

    @SneakyThrows(URISyntaxException.class)
    public ForwardProxyCallback(TigerProxy tigerProxy, TigerRoute tigerRoute) {
        super(tigerProxy, tigerRoute);
        targetUri = new URI(tigerRoute.getTo());
        if (targetUri.getPort() < 0) {
            port = tigerRoute.getTo().startsWith("https://") ? 443 : 80;
        } else {
            port = targetUri.getPort();
        }
        tigerProxy.addAlternativeName(new URI(tigerRoute.getFrom()).getHost());
    }

    @Override
    public HttpRequest handleRequest(HttpRequest req) {
        applyModifications(req);
        req.replaceHeader(header("Host", targetUri.getHost() + ":" + port));
        if (getTigerRoute().getBasicAuth() != null) {
            req.replaceHeader(
                header("Authorization",
                    getTigerRoute().getBasicAuth().toAuthorizationHeaderValue()));
        }
        final String path = req.getPath().toString().equals("/") ?
            targetUri.getPath()
            : targetUri.getPath() + req.getPath();
        return cloneRequest(req)
            .withPath(path)
            .withSecure(getTigerRoute().getTo().startsWith("https://"))
            .withQueryStringParameters(req.getQueryStringParameters());
    }

    @Override
    protected String extractProtocolAndHostForRequest(HttpRequest request) {
        return getTigerRoute().getFrom();
    }
}
