/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import org.mockserver.model.HttpRequest;

public class ForwardAllCallback extends AbstractTigerRouteCallback {

    public ForwardAllCallback(TigerProxy tigerProxy) {
        super(tigerProxy, null);
    }

    @Override
    protected HttpRequest handleRequest(HttpRequest req) {
        return forwardOverriddenRequest(
            req.withSocketAddress(
                req.isSecure(),
                req.socketAddressFromHostHeader().getHostName(),
                req.socketAddressFromHostHeader().getPort()
            )).getHttpRequest();
    }

    @Override
    protected String extractProtocolAndHostForRequest(HttpRequest request) {
        return request.getSocketAddress().getScheme() + "://" + request.getSocketAddress().getHost() + ":"
            + request.getSocketAddress().getPort();
    }

    boolean shouldLogTraffic() {
        return true;
    }
}
