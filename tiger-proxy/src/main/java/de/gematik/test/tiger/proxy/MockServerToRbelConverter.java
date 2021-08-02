/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.Arrays;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public class MockServerToRbelConverter {

    private final RbelConverter rbelConverter;

    public RbelElement convertResponse(HttpResponse response, String protocolAndHost) {
        log.trace("Converting response {}, headers {}, body {}", response,
            response.getHeaders(), response.getBodyAsString());
        return rbelConverter
            .parseMessage(responseToRbelMessage(response),
                convertUri(protocolAndHost), null);
    }

    public RbelElement convertRequest(HttpRequest request, String protocolAndHost) {
        log.trace("Converting request {}, headers {}, body {}", request,
            request.getHeaders(), request.getBodyAsString());
        return rbelConverter
            .parseMessage(requestToRbelMessage(request),
                null, convertUri(protocolAndHost));
    }

    private RbelHostname convertUri(String protocolAndHost) {
        try {
            URI uri = new URI(protocolAndHost);
            if (StringUtils.isEmpty(uri.getScheme())) {
                return null;
            } else {
                return RbelHostname.generateFromUrl(protocolAndHost);
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] responseToRbelMessage(final HttpResponse response) {
        byte[] httpResponseHeader = ("HTTP/1.1 " + response.getStatusCode() + " "
            + (response.getReasonPhrase() != null ? response.getReasonPhrase() : "") + "\r\n"
            + formatHeaderList(response.getHeaderList())
            + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);

        return Arrays.concatenate(httpResponseHeader, response.getBodyAsRawBytes());
    }

    private String formatHeaderList(List<Header> headerList) {
        return headerList.stream().map(h -> h.getValues().stream()
            .map(value -> h.getName().getValue() + ": " + value)
            .collect(Collectors.joining("\n")))
            .collect(Collectors.joining("\r\n"));
    }

    private byte[] requestToRbelMessage(final HttpRequest request) {
        byte[] httpRequestHeader = (request.getMethod().toString() + " " + getRequestUrl(request) + " HTTP/1.1\r\n"
            + formatHeaderList(request.getHeaderList())
            + "\r\n\r\n").getBytes();

        return Arrays.concatenate(httpRequestHeader, request.getBodyAsRawBytes());
    }

    private String getRequestUrl(HttpRequest request) {
        return request.getPath().getValue();
    }
}
