package de.gematik.test.tiger.proxy;

import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

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
    public HttpResponse handleResponse(HttpRequest req, HttpResponse resp) {
        applyModifications(resp);
        if (!getTigerRoute().isDisableRbelLogging()) {
            try {
                getTigerProxy().triggerListener(getTigerProxy().getMockServerToRbelConverter()
                    .convertRequest(req, getTigerRoute().getFrom()));
                getTigerProxy().triggerListener(getTigerProxy().getMockServerToRbelConverter()
                    .convertResponse(resp, getTigerRoute().getFrom(), req.getClientAddress()));
                getTigerProxy().manageRbelBufferSize();
            } catch (RuntimeException e) {
                propagateExceptionMessageSafe(e);
                log.error("RBel FAILED!", e);
            }
        }
        return resp;
    }
}
