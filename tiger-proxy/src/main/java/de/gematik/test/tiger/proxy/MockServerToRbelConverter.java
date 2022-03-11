/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.Arrays;
import org.mockserver.model.*;

@RequiredArgsConstructor
@Slf4j
public class MockServerToRbelConverter {

    private final RbelConverter rbelConverter;

    public RbelElement convertResponse(HttpResponse response, String serverProtocolAndHost,
        SocketAddress clientAddress) {
        log.trace("Converting response {}, headers {}, body {}", response,
            response.getHeaders(), response.getBodyAsString());
        return rbelConverter.parseMessage(responseToRbelMessage(response),
            convertUri(serverProtocolAndHost), convertSocketAdress(clientAddress));
    }

    public RbelElement convertRequest(HttpRequest request, String protocolAndHost) {
        log.trace("Converting request {}, headers {}, body {}", request,
            request.getHeaders(), request.getBodyAsString());
        return rbelConverter.parseMessage(requestToRbelMessage(request),
            convertSocketAdress(request.getClientAddress()), convertUri(protocolAndHost));
    }

    private RbelHostname convertSocketAdress(SocketAddress clientAddress) {
        if (clientAddress == null) {
            return null;
        }
        return RbelHostname.builder()
            .hostname(clientAddress.getHost())
            .port(clientAddress.getPort())
            .build();
    }

    private RbelHostname convertUri(String protocolAndHost) {
        try {
            new URI(protocolAndHost);
            return (RbelHostname) RbelHostname.generateFromUrl(protocolAndHost)
                .orElse(null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public RbelElement responseToRbelMessage(final HttpResponse response) {
        final byte[] httpMessage = responseToRawMessage(response);
        final RbelElement element = rbelConverter.convertElement(httpMessage, null);
        if (!element.hasFacet(RbelHttpResponseFacet.class)) {
            element.addFacet(RbelHttpResponseFacet.builder()
                .responseCode(RbelElement.builder()
                    .parentNode(element)
                    .rawContent(response.getStatusCode().toString().getBytes())
                    .build())
                .build());
        }

        return element;
    }

    public RbelElement requestToRbelMessage(final HttpRequest request) {
        final byte[] httpMessage = requestToRawMessage(request);
        final RbelElement element = rbelConverter.convertElement(httpMessage, null);
        if (!element.hasFacet(RbelHttpRequestFacet.class)) {
            element.addFacet(RbelHttpRequestFacet.builder()
                .path(RbelElement.wrap(element, request.getPath().getValue()))
                .method(RbelElement.wrap(element, request.getMethod().getValue()))
                .build());
        }

        return element;
    }

    private byte[] requestToRawMessage(HttpRequest request) {
        byte[] httpRequestHeader = (request.getMethod().toString() + " " + getRequestUrl(request) + " HTTP/1.1\r\n"
            + formatHeaderList(request.getHeaderList())
            + "\r\n\r\n").getBytes();

        final byte[] httpMessage = Arrays.concatenate(httpRequestHeader, request.getBodyAsRawBytes());
        return httpMessage;
    }

    private byte[] responseToRawMessage(HttpResponse response) {
        byte[] httpResponseHeader = ("HTTP/1.1 " + response.getStatusCode() + " "
            + (response.getReasonPhrase() != null ? response.getReasonPhrase() : "") + "\r\n"
            + formatHeaderList(response.getHeaderList())
            + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);

        final byte[] httpMessage = Arrays.concatenate(httpResponseHeader, response.getBodyAsRawBytes());
        return httpMessage;
    }

    private String formatHeaderList(List<Header> headerList) {
        return headerList.stream().map(h -> h.getValues().stream()
                .map(value -> h.getName().getValue() + ": " + value)
                .collect(Collectors.joining("\r\n")))
            .collect(Collectors.joining("\r\n"));
    }

    private String getRequestUrl(HttpRequest request) {
        StringJoiner pathToQueryJoiner = new StringJoiner("?");
        if (StringUtils.isEmpty(request.getPath().getValue())) {
            pathToQueryJoiner.add("/");
        } else {
            pathToQueryJoiner.add(request.getPath().getValue());
        }

        if (request.getQueryStringParameters() != null
            && request.getQueryStringParameters().getEntries() != null) {
            StringJoiner queryParameterJoiner = new StringJoiner("&");
            for (Parameter param : request.getQueryStringParameters().getEntries()) {
                for (NottableString value : param.getValues()) {
                    StringJoiner parameterJoiner = new StringJoiner("=");
                    parameterJoiner.add(param.getName().toString());
                    parameterJoiner.add(value.toString());
                    queryParameterJoiner.add(parameterJoiner.toString());
                }
            }
            pathToQueryJoiner.add(queryParameterJoiner.toString());
        }

        return pathToQueryJoiner.toString();
    }
}
