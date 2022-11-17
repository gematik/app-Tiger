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

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpHeaderFacet;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MessageConverterTest {

    @Test
    public void convertMessage_shouldGiveCorrectType() throws IOException {
        final String curlMessage = readCurlFromFileWithCorrectedLineBreaks
            ("src/test/resources/sampleMessages/jsonMessage.curl");

        final RbelElement convertedMessage = RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);

        assertThat(convertedMessage.getFacet(RbelHttpResponseFacet.class))
            .isPresent();
    }

    @Test
    public void noReasonPhrase_shouldGiveEmptyOptional() throws IOException {
        final String curlMessage = readCurlFromFileWithCorrectedLineBreaks
            ("src/test/resources/sampleMessages/jsonMessage.curl");

        final RbelElement convertedMessage = RbelLogger.build().getRbelConverter().convertElement(curlMessage, null);

        assertThat(convertedMessage.getFacet(RbelHttpResponseFacet.class).get().getReasonPhrase().getRawStringContent())
            .isEqualTo(null);
    }

    @Test
    public void convertMessage_shouldGiveHeaderFields() throws IOException {
        final String curlMessage = readCurlFromFileWithCorrectedLineBreaks
            ("src/test/resources/sampleMessages/jsonMessage.curl");

        final RbelElement convertedMessage = RbelLogger.build().getRbelConverter()
            .convertElement(curlMessage, null);

        final Map<String, RbelElement> elementMap = convertedMessage
            .getFacetOrFail(RbelHttpMessageFacet.class).getHeader()
            .getFacetOrFail(RbelHttpHeaderFacet.class);
        assertThat(elementMap)
            .hasSize(3);
        assertThat(elementMap.get("Content-Type").getRawStringContent())
            .isEqualTo("application/json; charset=latin1");
    }
}
