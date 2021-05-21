/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.*;
import java.util.HashMap;
import java.util.stream.Collectors;
import lombok.Data;
import org.apache.http.HttpHeaders;
import org.mockserver.mappers.MockServerHttpRequestToFullHttpRequest;
import org.mockserver.model.*;

@Data
public class MockServerToRbelConverter {

    private final RbelLogger rbelLogger;

    public RbelHttpResponse convertResponse(HttpResponse response) {
        return (RbelHttpResponse) convertMessage(
            RbelHttpResponse.builder()
                .responseCode(response.getStatusCode())
                .header(mapHeader(response.getHeaders()))
                .body(convertBody(response.getBody(), response.getHeaders()))
                .build()
                .setRawMessage("HTTP/1.1 " + response.getStatusCode() + " "
                    + (response.getReasonPhrase() != null ? response.getReasonPhrase() : "") + "\n"
                    + response.getHeaders().getEntries().stream().map(Header::toString)
                    .collect(Collectors.joining("\n"))
                    + "\n\n" + response.getBodyAsString()));
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
            return rbelLogger.getRbelConverter().convertMessage(new String(body.getRawBytes()));
        }
    }

    private RbelElement convertMessage(RbelElement input) {
        return rbelLogger.getRbelConverter().convertMessage(input);
    }

    public RbelHttpRequest convertRequest(HttpRequest request, String protocolAndHost) {
        return (RbelHttpRequest) convertMessage(
            RbelHttpRequest.builder()
                .method(request.getMethod().getValue())
                .path((RbelUriElement) rbelLogger.getRbelConverter().convertMessage(
                    buildOriginalRequestUri(request, protocolAndHost)))
                .header(mapHeader(request.getHeaders()))
                .body(convertBody(request.getBody(), request.getHeaders()))
                .build()
                .setRawMessage(request.getMethod().toString() + " " + request.getPath().getValue() + " HTTP/1.1\n"
                    + request.getHeaders().getEntries().stream().map(Header::toString)
                    .collect(Collectors.joining("\n")) + "\n\n"
                    + request.getBodyAsString()));
    }

    private String buildOriginalRequestUri(HttpRequest request, String protocolAndHost) {
        return protocolAndHost + new MockServerHttpRequestToFullHttpRequest(null)
            .mapMockServerRequestToNettyRequest(request)
            .uri();
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
