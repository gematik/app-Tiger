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
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelUriFacet;
import de.gematik.rbellogger.data.facet.RbelUriParameterFacet;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyModificationException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Parameters;

@RequiredArgsConstructor
@Data
@Slf4j
public abstract class AbstractTigerRouteCallback implements ExpectationForwardAndResponseCallback {

    private final TigerProxy tigerProxy;
    private final TigerRoute tigerRoute;

    public void applyModifications(HttpRequest request) {
        final RbelElement requestElement = tigerProxy.getMockServerToRbelConverter().requestToRbelMessage(request);
        final RbelElement modifiedRequest = tigerProxy.getRbelLogger().getRbelModifier()
            .applyModifications(requestElement);
        if (modifiedRequest == requestElement) {
            return;
        }
        request.withBody(extractSafe(modifiedRequest, "$.body").getRawContent());
        for (RbelElement modifiedHeader : modifiedRequest.findRbelPathMembers("$.header.*")) {
            request = request.replaceHeader(header(modifiedHeader.getKey().orElseThrow(),
                modifiedHeader.getRawStringContent()));
        }
        final RbelUriFacet uriFacet = extractSafe(modifiedRequest, "$.path").getFacetOrFail(RbelUriFacet.class);
        request.withPath(uriFacet.getBasicPathString());
        clearExistingQueryParameters(request);
        addAllQueryParametersFromRbelMessage(request, uriFacet);
        request.withMethod(extractSafe(modifiedRequest, "$.method").getRawStringContent());
    }

    private RbelElement extractSafe(RbelElement targetElement, String rbelPath) {
        return targetElement.findElement(rbelPath)
            .orElseThrow(() ->
                new TigerProxyModificationException("Unexpected structure: Could not find '" + rbelPath + "'!"));
    }

    private void addAllQueryParametersFromRbelMessage(HttpRequest request, RbelUriFacet uriFacet) {
        for (RbelElement queryElement : uriFacet.getQueryParameters()) {
            final RbelUriParameterFacet parameterFacet = queryElement.getFacetOrFail(RbelUriParameterFacet.class);
            request.withQueryStringParameter(
                parameterFacet.getKeyAsString(),
                parameterFacet.getValue().getRawStringContent());
        }
    }

    private void clearExistingQueryParameters(HttpRequest request) {
        final Parameters queryStringParameters = request.getQueryStringParameters();
        if (queryStringParameters == null) {
            return;
        }
        queryStringParameters.getEntries().stream()
            .forEach(parameter -> queryStringParameters.remove(parameter.getName()));
    }

    public void applyModifications(HttpResponse response) {
        final RbelElement responseElement = tigerProxy.getMockServerToRbelConverter().responseToRbelMessage(response);
        final RbelElement modifiedResponse = tigerProxy.getRbelLogger().getRbelModifier()
            .applyModifications(responseElement);
        if (modifiedResponse == responseElement) {
            return;
        }
        response.withBody(extractSafe(modifiedResponse, "$.body").getRawContent());
        for (RbelElement modifiedHeader : modifiedResponse.findRbelPathMembers("$.header.*")) {
            response = response.replaceHeader(header(modifiedHeader.getKey().orElseThrow(),
                modifiedHeader.getRawStringContent()));
        }
        response.withStatusCode(
            Integer.parseInt(extractSafe(modifiedResponse, "$.responseCode").getRawStringContent()));
        final String reasonPhrase = extractSafe(modifiedResponse, "$.reasonPhrase").getRawStringContent();
        if (!StringUtils.isEmpty(reasonPhrase)) {
            response.withReasonPhrase(reasonPhrase);
        } else {
            response.withReasonPhrase(" ");
        }
    }

    @Override
    public final HttpRequest handle(HttpRequest req) {
        try {
            return handleRequest(req);
        } catch (RuntimeException e) {
            propagateExceptionMessageSafe(e);
            throw e;
        }
    }

    public void propagateExceptionMessageSafe(Exception exception) {
        try {
            tigerProxy.propagateException(exception);
        } catch (Exception handlingException) {
            log.warn("While propagating an exception another error occured (ignoring):", handlingException);
        }
    }

    protected abstract HttpRequest handleRequest(HttpRequest req);

    @Override
    public final HttpResponse handle(HttpRequest req, HttpResponse resp) {
        try {
            return handleResponse(req, resp);
        } catch (RuntimeException e) {
            propagateExceptionMessageSafe(e);
            throw e;
        }
    }

    protected abstract HttpResponse handleResponse(HttpRequest req, HttpResponse resp);
}