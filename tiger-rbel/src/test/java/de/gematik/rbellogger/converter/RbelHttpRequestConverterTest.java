/*
 * Copyright (c) 2022 gematik GmbH
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

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpRequestFacet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class RbelHttpRequestConverterTest {

    private RbelConverter rbelConverter = RbelLogger.build().getRbelConverter();

    @Test
    @DisplayName("should convert CURL request with defunct linebreaks")
    public void shouldConvertCurlRequestWithDefunctLinebreaks() {
        final RbelElement rbelElement = new RbelElement(("GET /auth/realms/idp/.well-known/openid-configuration HTTP/1.1\n"
                + "Accept: */*\n"
                + "Host: localhost:8080\n"
                + "Connection: Keep-Alive\n"
                + "User-Agent: Apache-HttpClient/4.5.12 (Java/11.0.8)\n"
                + "Accept-Encoding: gzip,deflate\n").getBytes(StandardCharsets.UTF_8), null);

        new RbelHttpRequestConverter().consumeElement(rbelElement, rbelConverter);

        assertThat(rbelElement.hasFacet(RbelHttpRequestFacet.class)).isTrue();
        assertThat(rbelElement.hasFacet(RbelHttpMessageFacet.class)).isTrue();
    }

    @Test
    public void doubleHeaderValueRequest() {
        final RbelElement rbelElement = new RbelElement(("GET /auth/realms/idp/.well-known/openid-configuration HTTP/1.1\r\n"
                + "User-Agent: Value1\r\n"
                + "User-Agent: Value2\r\n\r\n").getBytes(StandardCharsets.UTF_8), null);

        rbelConverter.convertElement(rbelElement);

        assertThat(rbelElement.findRbelPathMembers("$.header.User-Agent")).hasSize(2);
        assertThat(rbelElement.findRbelPathMembers("$.header.*").get(0).getRawStringContent())
            .isEqualTo("Value1");
        assertThat(rbelElement.findRbelPathMembers("$.header.*").get(1).getRawStringContent())
            .isEqualTo("Value2");
    }

    @Test
    public void doubleHeaderValueResponse() {
        final RbelElement rbelElement = new RbelElement(("HTTP/1.1 200\r\n"
                + "User-Agent: Value1\r\n"
                + "User-Agent: Value2\r\n\r\n").getBytes(StandardCharsets.UTF_8), null);

        rbelConverter.convertElement(rbelElement);

        assertThat(rbelElement.findRbelPathMembers("$.header.User-Agent")).hasSize(2);
        assertThat(rbelElement.findRbelPathMembers("$.header.*").get(0).getRawStringContent())
            .isEqualTo("Value1");
        assertThat(rbelElement.findRbelPathMembers("$.header.*").get(1).getRawStringContent())
            .isEqualTo("Value2");
    }

    @Test
    public void shouldNotConvertAcceptList() {
        final RbelElement rbelElement = new RbelElement(("GET,PUT,POST\"").getBytes(StandardCharsets.UTF_8), null);

        new RbelHttpRequestConverter().consumeElement(rbelElement, rbelConverter);

        assertThat(rbelElement.hasFacet(RbelHttpRequestFacet.class)).isFalse();
    }
}
