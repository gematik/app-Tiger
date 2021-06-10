package de.gematik.test.tiger.lib.rest;

import de.gematik.rbellogger.data.*;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RbelRestAssueredResponse extends RbelHttpResponse {

    public RbelRestAssueredResponse(final RbelMultiValuedMapElement header,
        final RbelElement body, final int responseCode) {
        super(header, body, responseCode);
    }

    public static RbelRestAssueredResponse fromResponse(final Response response) {
        return new RbelRestAssueredResponse(
            mapHeader(response.getHeaders()),
            new RbelBinaryElement(response.getBody().asByteArray()),
            response.statusCode());
    }

    private static RbelMultiValuedMapElement mapHeader(final Headers headers) {
        final Map<String, List<RbelElement>> mapHeaders = new HashMap<>();
        headers.asList().forEach(header -> {
            mapHeaders.computeIfAbsent(header.getName(), key -> new ArrayList<>());
            mapHeaders.get(header.getName()).add(new RbelStringElement(header.getValue()));
        });
        return new RbelMultiValuedMapElement(mapHeaders);
    }
}
