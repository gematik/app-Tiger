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
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;

public class RbelBearerTokenConverterTest {

    @Test
    public void shouldFindJwtInBearerHeaderAttributer() throws IOException {
        final String curlMessage = readCurlFromFileWithCorrectedLineBreaks
            ("src/test/resources/sampleMessages/bearerToken.curl");

        final RbelLogger logger = RbelLogger.build();
        final RbelElement convertedMessage = logger.getRbelConverter()
            .parseMessage(curlMessage.getBytes(StandardCharsets.UTF_8), null, null, Optional.of(ZonedDateTime.now()));

        assertThat(convertedMessage.findRbelPathMembers("$.header.Authorization.BearerToken"))
            .isNotEmpty();
    }

    @Test
    public void shouldRenderBearerToken() throws IOException {
        final String curlMessage = readCurlFromFileWithCorrectedLineBreaks
            ("src/test/resources/sampleMessages/bearerToken.curl");

        final RbelLogger logger = RbelLogger.build();
        logger.getRbelConverter().parseMessage(curlMessage.getBytes(StandardCharsets.UTF_8), null, null, Optional.of(ZonedDateTime.now()));

        final String renderingOutput = RbelHtmlRenderer.render(logger.getMessageHistory());
        assertThat(renderingOutput)
            .contains("Carvalho")
            .contains("https://idp.zentral.idp.splitdns.ti-dienste.de");
        FileUtils.writeStringToFile(new File("target/bearerToken.html"), renderingOutput);
    }
}
