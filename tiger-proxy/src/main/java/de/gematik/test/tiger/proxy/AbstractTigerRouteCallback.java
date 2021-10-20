package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelUriFacet;
import de.gematik.rbellogger.data.facet.RbelUriParameterFacet;
import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Parameters;

import static org.mockserver.model.Header.header;

@RequiredArgsConstructor
@Data
public abstract class AbstractTigerRouteCallback implements ExpectationForwardAndResponseCallback {

    private final TigerProxy tigerProxy;
    private final TigerRoute tigerRoute;

    public void applyModifications(HttpRequest request) {
        final RbelElement requestElement = tigerProxy.getMockServerToRbelConverter().requestToRbelMessage(request);
        final RbelElement modifiedRequest = tigerProxy.getRbelLogger().getRbelModifier().applyModifications(requestElement);
        if (modifiedRequest == requestElement) {
            return;
        }
        request.withBody(modifiedRequest.findElement("$.body").get().getRawContent());
        for (RbelElement modifiedHeader : modifiedRequest.findRbelPathMembers("$.header.*")) {
            request = request.replaceHeader(header(modifiedHeader.getKey().orElseThrow(),
                modifiedHeader.getRawStringContent()));
        }
        final RbelUriFacet uriFacet = modifiedRequest.findElement("$.path").get().getFacetOrFail(RbelUriFacet.class);
        request.withPath(uriFacet.getBasicPathString());
        clearExistingQueryParameters(request);
        addAllQueryParametersFromRbelMessage(request, uriFacet);
        request.withMethod(modifiedRequest.findElement("$.method").get().getRawStringContent());
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
        final RbelElement modifiedResponse = tigerProxy.getRbelLogger().getRbelModifier().applyModifications(responseElement);
        if (modifiedResponse == responseElement) {
            return;
        }
        response.withBody(modifiedResponse.findElement("$.body").get().getRawContent());
        for (RbelElement modifiedHeader : modifiedResponse.findRbelPathMembers("$.header.*")) {
            response = response.replaceHeader(header(modifiedHeader.getKey().orElseThrow(),
                modifiedHeader.getRawStringContent()));
        }
        response.withStatusCode(Integer.parseInt(modifiedResponse.findElement("$.responseCode").get().getRawStringContent()));
    }
}
