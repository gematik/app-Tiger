/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.proxy.handler;

import static org.mockserver.model.Header.header;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.proxy.TigerProxy;
import java.net.MalformedURLException;
import java.net.URL;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.NottableString;

@Slf4j
public class ForwardProxyCallback extends AbstractTigerRouteCallback {

    private final URL targetUrl;
    private final URL sourceUrl;
    private final int port;
    private final boolean addTrailingSlash;

    @SneakyThrows(MalformedURLException.class)
    public ForwardProxyCallback(TigerProxy tigerProxy, TigerRoute tigerRoute) {
        super(tigerProxy, tigerRoute);
        if (tigerRoute.getTo().endsWith("/")) {
            targetUrl = new URL(tigerRoute.getTo().substring(0, tigerRoute.getTo().length() - 1));
            addTrailingSlash = true;
        } else {
            targetUrl = new URL(tigerRoute.getTo());
            addTrailingSlash = false;
        }
        sourceUrl = new URL(tigerRoute.getFrom());
        if (targetUrl.getPort() < 0) {
            port = tigerRoute.getTo().startsWith("https://") ? 443 : 80;
        } else {
            port = targetUrl.getPort();
        }
        tigerProxy.addAlternativeName(sourceUrl.getHost());
    }

    @Override
    public HttpRequest handleRequest(HttpRequest req) {
        applyModifications(req);
        req.replaceHeader(header("Host", targetUrl.getHost() + ":" + port));
        if (getTigerRoute().getBasicAuth() != null) {
            req.replaceHeader(
                header("Authorization",
                    getTigerRoute().getBasicAuth().toAuthorizationHeaderValue()));
        }
        final String path = req.getPath().toString().equals("/") ?
            targetUrl.getPath() + "/"
            : targetUrl.getPath() + req.getPath();
        return cloneRequest(req)
            .withPath(path)
            .withSecure(getTigerRoute().getTo().startsWith("https://"))
            .withQueryStringParameters(req.getQueryStringParameters());
    }

    @Override
    protected String extractProtocolAndHostForRequest(HttpRequest request) {
        return sourceUrl.getProtocol() + "://" + sourceUrl.getHost();
    }
}
