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

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
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
        String clientAddress) {
        if (log.isTraceEnabled()) {
            log.trace("Converting response {}, headers {}, body {}", response,
                response.getHeaders(), response.getBodyAsString());
        }

        final RbelElement element = rbelConverter.parseMessage(responseToRbelMessage(response),
            convertUri(serverProtocolAndHost), RbelHostname.fromString(clientAddress).orElse(null),
            Optional.of(ZonedDateTime.now()));

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

    public RbelElement convertRequest(HttpRequest request, String protocolAndHost) {
        if (log.isTraceEnabled()) {
            log.trace("Converting request {}, headers {}, body {}", request,
                request.getHeaders(), request.getBodyAsString());
        }

        final RbelElement element = rbelConverter.parseMessage(
            requestToRbelMessage(request),
            RbelHostname.fromString(request.getRemoteAddress()).orElse(null),
            convertUri(protocolAndHost),
            Optional.of(ZonedDateTime.now()));

        if (!element.hasFacet(RbelHttpRequestFacet.class)) {
            element.addFacet(RbelHttpRequestFacet.builder()
                .path(RbelElement.wrap(element, request.getPath().getValue()))
                .method(RbelElement.wrap(element, request.getMethod().getValue()))
                .build());
        }

        return element;
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
        return RbelElement.builder()
            .rawContent(httpMessage)
            .build();
    }

    public RbelElement requestToRbelMessage(final HttpRequest request) {
        final byte[] httpMessage = requestToRawMessage(request);
        return RbelElement.builder()
            .rawContent(httpMessage)
            .build();
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
