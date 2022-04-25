/*
 * Copyright (c) 2022 gematik GmbH
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

package de.gematik.test.tiger.proxy;

import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpOverrideForwardedRequest.forwardOverriddenRequest;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.Optional;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.SocketAddress;

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
        return forwardOverriddenRequest(req)
            .getHttpRequest()
            .withPath(path)
            .withSecure(getTigerRoute().getTo().startsWith("https://"))
            .withQueryStringParameters(req.getQueryStringParameters());
    }

    @Override
    protected String extractProtocolAndHostForRequest(HttpRequest request) {
        return getTigerRoute().getFrom();
    }
}
