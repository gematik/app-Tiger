/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelMessage;
import de.gematik.rbellogger.data.elements.*;
import java.util.HashMap;
import java.util.stream.Collectors;
import lombok.Data;
import org.apache.http.HttpHeaders;
import org.mockserver.mappers.MockServerHttpRequestToFullHttpRequest;
import org.mockserver.mappers.MockServerHttpResponseToFullHttpResponse;
import org.mockserver.model.*;

@Data
public class MockServerToRbelConverter {

    private final RbelLogger rbelLogger;

    public RbelMessage convertResponse(HttpResponse response, String protocolAndHost) {
        final RbelHttpResponse rbelHttpResponse = (RbelHttpResponse) convertMessage(
            RbelHttpResponse.builder()
                .responseCode(response.getStatusCode())
                .header(mapHeader(response.getHeaders()))
                .body(convertBody(response.getBody(), response.getHeaders()))
                .rawBody(response.getBodyAsRawBytes())
                .build()
                .setRawMessage(buildOriginalContent(response)));
        final RbelMessage rbelMessage = rbelLogger.getRbelConverter().parseMessage(rbelHttpResponse,
            RbelHostname.generateFromUrl(protocolAndHost), null);
        return rbelMessage;
    }

    private RbelElement convertBody(Body body, Headers headers) {
        if (body == null || body.getRawBytes() == null || body.getRawBytes().length == 0) {
            return new RbelNullElement();
        }
        if (headers.getValues(HttpHeaders.CONTENT_TYPE).stream()
            .filter(v -> v.startsWith(MediaType.APPLICATION_BINARY.toString())
                || v.startsWith(MediaType.APPLICATION_OCTET_STREAM.toString()))
            .findAny().isPresent()) {
            return convertMessage(new RbelBinaryElement(body.getRawBytes()));
        } else {
            return rbelLogger.getRbelConverter().convertElement(new String(body.getRawBytes()));
        }
    }

    private RbelElement convertMessage(RbelElement input) {
        return rbelLogger.getRbelConverter().convertElement(input);
    }

    public RbelMessage convertRequest(HttpRequest request, String protocolAndHost) {
        final RbelHttpRequest rbelHttpRequest = (RbelHttpRequest) convertMessage(
            RbelHttpRequest.builder()
                .method(request.getMethod().getValue())
                .path((RbelUriElement) rbelLogger.getRbelConverter().convertElement(
                    buildOriginalRequestUri(request, protocolAndHost)))
                .header(mapHeader(request.getHeaders()))
                .body(convertBody(request.getBody(), request.getHeaders()))
                .rawBody(request.getBodyAsRawBytes())
                .build()
//                .setRawMessage(request.getMethod().toString() + " " + request.getPath().getValue() + " HTTP/1.1\n"
//                    + request.getHeaders().getEntries().stream().map(Header::toString)
//                    .collect(Collectors.joining("\n")) + "\n\n"
//                    + request.getBodyAsString())
                .setRawMessage(buildOriginalContent(request)));

        final RbelMessage rbelMessage = rbelLogger.getRbelConverter().parseMessage(rbelHttpRequest,
            null, RbelHostname.generateFromUrl(protocolAndHost));
        return rbelMessage;
    }

    private String buildOriginalRequestUri(HttpRequest request, String protocolAndHost) {
        return protocolAndHost + new MockServerHttpRequestToFullHttpRequest(null)
            .mapMockServerRequestToNettyRequest(request)
            .uri();
    }

    private String buildOriginalContent(HttpRequest request) {
        return new MockServerHttpRequestToFullHttpRequest(null)
            .mapMockServerRequestToNettyRequest(request)
            .toString()
            .split("\\n", 2)[1];
    }

    private String buildOriginalContent(HttpResponse response) {
        final String str = new MockServerHttpResponseToFullHttpResponse(null)
            .mapMockServerResponseToNettyResponse(response)
            .toString()
            .split("\\n", 2)[1]; // strip the intro
        return str.substring(0, str.length() - 1); // strip the trailing ]
    }

    private RbelMultiValuedMapElement mapHeader(Headers headers) {
        RbelMultiValuedMapElement result = new RbelMultiValuedMapElement(new HashMap<>());
        for (Header entry : headers.getEntries()) {
            entry.getValues().forEach(str ->
                result.put(entry.getName().getValue(), new RbelStringElement(str.getValue())));
        }
        return result;
    }
}
