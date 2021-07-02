/*
 * Copyright (c) 2021 gematik GmbH
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

package de.gematik.test.tiger.lib.rest;

import de.gematik.rbellogger.data.elements.*;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;

public class RbelRestAssuredResponse extends RbelHttpResponse {

    @Builder (builderMethodName = "restAssuredResponseBuilder")
    public RbelRestAssuredResponse(RbelMultiValuedMapElement header, RbelElement body, int responseCode,
        String rawMessage, byte[] rawBody) {
        super(header, body, responseCode, rawMessage, rawBody);
    }

    public static RbelRestAssuredResponse fromResponse(final Response response) {
        return restAssuredResponseBuilder()
            .header(mapHeader(response.getHeaders()))
            .body(new RbelBinaryElement(response.getBody().asByteArray()))
            .responseCode(response.statusCode())
            .rawBody(response.getBody().asByteArray())
            .rawMessage(response.asString())
            .build();
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
