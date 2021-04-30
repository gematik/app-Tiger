package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHttpRequest;
import de.gematik.rbellogger.data.RbelHttpResponse;
import de.gematik.rbellogger.data.RbelMultiValuedMapElement;
import de.gematik.rbellogger.data.RbelPathElement;
import de.gematik.rbellogger.data.RbelStringElement;
import java.util.HashMap;
import java.util.stream.Collectors;
import lombok.Data;
import org.mockserver.model.Header;
import org.mockserver.model.Headers;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

@Data
public class MockServerToRbelConverter {

    private final RbelLogger rbelLogger;

    public RbelHttpResponse convertResponse(HttpResponse response) {
        return (RbelHttpResponse) convertMessage(
            RbelHttpResponse.builder()
                .responseCode(response.getStatusCode())
                .header(mapHeader(response.getHeaders()))
                .body(convertMessage(response.getBodyAsString()))
                .build()
                .setRawMessage("HTTP/1.1 " + response.getStatusCode() + " "
                    + (response.getReasonPhrase() != null ? response.getReasonPhrase() : "") + "\n"
                    + response.getHeaders().getEntries().stream().map(Header::toString)
                    .collect(Collectors.joining("\n"))
                    + "\n\n" + response.getBodyAsString()));
    }

    private RbelElement convertMessage(RbelElement input) {
        return rbelLogger.getRbelConverter().convertMessage(input);
    }

    private RbelElement convertMessage(String input) {
        return rbelLogger.getRbelConverter().convertMessage(input);
    }

    public RbelHttpRequest convertRequest(HttpRequest request) {
        return (RbelHttpRequest) convertMessage(
            RbelHttpRequest.builder()
                .method(request.getMethod().getValue())
                .path((RbelPathElement) convertMessage(request.getPath().getValue()))
                .header(mapHeader(request.getHeaders()))
                .body(new RbelStringElement(request.getBodyAsString()))
                .build()
                .setRawMessage(request.getMethod().toString() + " " + request.getPath().getValue() + " HTTP/1.1\n"
                    + request.getHeaders().getEntries().stream().map(Header::toString)
                    .collect(Collectors.joining("\n")) + "\n\n"
                    + request.getBodyAsString()));
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
