/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.handler;

import static org.mockserver.model.Header.header;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
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
            final URI newUri = new URI(this.targetUrl.getPath())
                .relativize(new URI(originalLocation));
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
