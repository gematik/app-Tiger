/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static org.mockserver.model.Header.header;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.data.facet.RbelMessageTimingFacet;
import de.gematik.rbellogger.data.facet.RbelUriFacet;
import de.gematik.rbellogger.data.facet.RbelUriParameterFacet;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyModificationException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
    private final Map<String, ZonedDateTime> requestTimingMap = new HashMap<>();

    public void applyModifications(HttpRequest request) {
        if (!tigerProxy.getModifications().isEmpty()) {
            parseMessageAndApplyModifications(request);
        }
    }

    public void parseMessageAndApplyModifications(HttpRequest request) {
        final RbelElement requestElement = tigerProxy.getRbelLogger().getRbelConverter().convertElement(
            tigerProxy.getMockServerToRbelConverter().requestToRbelMessage(request));
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
        if (!tigerProxy.getModifications().isEmpty()) {
            parseMessageAndApplyModifications(response);
        }
    }

    public void parseMessageAndApplyModifications(HttpResponse response) {
        final RbelElement responseElement = tigerProxy.getRbelLogger().getRbelConverter().convertElement(
            tigerProxy.getMockServerToRbelConverter().responseToRbelMessage(response));
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
            requestTimingMap.put(req.getLogCorrelationId(), ZonedDateTime.now());
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
            final HttpResponse httpResponse = handleResponse(req, resp);
            requestTimingMap.remove(req);
            return httpResponse;
        } catch (RuntimeException e) {
            propagateExceptionMessageSafe(e);
            throw e;
        }
    }

    public HttpResponse handleResponse(HttpRequest req, HttpResponse resp) {
        applyModifications(resp);
        if (shouldLogTraffic()) {
            try {
                final RbelElement request = getTigerProxy().getMockServerToRbelConverter()
                    .convertRequest(req, extractProtocolAndHostForRequest(req));
                final RbelElement response = getTigerProxy().getMockServerToRbelConverter()
                    .convertResponse(resp, extractProtocolAndHostForRequest(req), req.getClientAddress());
                Optional.ofNullable(getRequestTimingMap().get(req.getLogCorrelationId()))
                    .ifPresent(requestTime -> addTimingFacet(request, requestTime));
                addTimingFacet(response, ZonedDateTime.now());
                response.addOrReplaceFacet(
                    response.getFacet(RbelHttpResponseFacet.class)
                        .map(RbelHttpResponseFacet::toBuilder)
                        .orElse(RbelHttpResponseFacet.builder())
                        .request(request)
                        .build());

                getTigerProxy().triggerListener(request);
                getTigerProxy().triggerListener(response);
            } catch (RuntimeException e) {
                propagateExceptionMessageSafe(e);
                log.error("Rbel-parsing failed!", e);
            }
        }
        return resp;
    }

    boolean shouldLogTraffic() {
        return !getTigerRoute().isDisableRbelLogging();
    }

    protected abstract String extractProtocolAndHostForRequest(HttpRequest request);

    private RbelElement addTimingFacet(RbelElement message, ZonedDateTime requestTime) {
        return message.addFacet(RbelMessageTimingFacet.builder()
            .transmissionTime(requestTime)
            .build());
    }
}
