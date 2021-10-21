package de.gematik.test.tiger.proxy;

import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.net.URI;
import java.net.URISyntaxException;

import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;

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
    public HttpRequest handle(HttpRequest req) {
        applyModifications(req);
        req.replaceHeader(header("Host", targetUri.getHost() + ":" + port));
        if (getTigerRoute().getBasicAuth() != null) {
            req.replaceHeader(
                header("Authorization",
                    getTigerRoute().getBasicAuth().toAuthorizationHeaderValue()));
        }
        final String path = req.getPath().equals("/") ?
            targetUri.getPath()
            : targetUri.getPath() + req.getPath();
        return forwardOverriddenRequest(req)
            .getHttpRequest()
            .withPath(path)
            .withSecure(getTigerRoute().getTo().startsWith("https://"))
            .withQueryStringParameters(req.getQueryStringParameters());
    }

    @Override
    public HttpResponse handle(HttpRequest req, HttpResponse resp) {
        applyModifications(resp);
        if (!getTigerRoute().isDisableRbelLogging()) {
            try {
                getTigerProxy().triggerListener(getTigerProxy().getMockServerToRbelConverter()
                    .convertRequest(req, getTigerRoute().getFrom()));
                getTigerProxy().triggerListener(getTigerProxy().getMockServerToRbelConverter()
                    .convertResponse(resp, getTigerRoute().getFrom()));
                getTigerProxy().manageRbelBufferSize();
            } catch (Exception e) {
                log.error("RBel FAILED!", e);
            }
        }
        return resp;
    }
}
