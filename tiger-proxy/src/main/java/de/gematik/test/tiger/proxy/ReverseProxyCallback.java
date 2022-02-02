package de.gematik.test.tiger.proxy;

import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.net.URI;
import java.net.URISyntaxException;

@Slf4j
public class ReverseProxyCallback extends AbstractTigerRouteCallback {

    private final URI targetUri;
    private final int port;

    @SneakyThrows(URISyntaxException.class)
    public ReverseProxyCallback(TigerProxy tigerProxy, TigerRoute route) {
        super(tigerProxy, route);
        this.targetUri = new URI(route.getTo());
        if (targetUri.getPort() < 0) {
            port = route.getTo().startsWith("https://") ? 443 : 80;
        } else {
            port = targetUri.getPort();
        }

    }

    @Override
    public HttpRequest handleRequest(HttpRequest httpRequest) {
        applyModifications(httpRequest);
        final HttpRequest request = httpRequest.withSocketAddress(
                getTigerRoute().getTo().startsWith("https://"),
                targetUri.getHost(),
                port
            ).withSecure(getTigerRoute().getTo().startsWith("https://"))
            .removeHeader("Host")
            .withPath(patchPath(httpRequest.getPath().getValue()));

        request.withHeader("Host", targetUri.getHost() + ":" + port);
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
    public HttpResponse handleResponse(HttpRequest httpRequest, HttpResponse httpResponse) {
        applyModifications(httpResponse);
        if (!getTigerRoute().isDisableRbelLogging()) {
            try {
                getTigerProxy().triggerListener(getTigerProxy().getMockServerToRbelConverter()
                    .convertRequest(httpRequest, getTigerRoute().getTo()));
                getTigerProxy().triggerListener(getTigerProxy().getMockServerToRbelConverter()
                    .convertResponse(httpResponse, getTigerRoute().getTo(), httpRequest.getClientAddress()));
            } catch (RuntimeException e) {
                propagateExceptionMessageSafe(e);
                log.error("RBel FAILED!", e);
            }
        }
        return httpResponse;
    }
}
