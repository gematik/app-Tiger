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

package de.gematik.rbellogger.converter;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RbelMtomConverterTest {

    private RbelLogger rbelLogger;

    @BeforeEach
    @SneakyThrows
    public void init() {
        try (final RbelFileReaderCapturer rbelFileReaderCapturer = getRbelFileReaderCapturer(
        )) {

            rbelLogger = RbelLogger.build(new RbelConfiguration()
                .setActivateAsn1Parsing(false)
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                .addAdditionalConverter(new RbelVauEpaConverter())
                .addCapturer(rbelFileReaderCapturer));
            rbelFileReaderCapturer.initialize();
        }
    }

    private static RbelFileReaderCapturer getRbelFileReaderCapturer() {
        return RbelFileReaderCapturer.builder()
            .rbelFile("src/test/resources/vauEp2FlowUnixLineEnding.tgr")
            .build();
    }

    @Test
    void shouldRenderCleanHtml() {
        assertThat(RbelHtmlRenderer.render(rbelLogger.getMessageHistory()))
            .isNotBlank();
    }

    @Test
    @DisplayName("MTOM XML - should be parsed correctly")
    void mtomXml_shouldBeParsedCorrectly() {
        assertThat(rbelLogger.getMessageList().get(34)
            .findRbelPathMembers("$..Envelope.soap").get(0)
            .getRawStringContent())
            .isEqualTo("http://www.w3.org/2003/05/soap-envelope");

        assertThat(rbelLogger.getMessageList().get(34)
            .findRbelPathMembers("$..EncryptionMethod.Algorithm")
            .stream()
            .map(RbelElement::getRawStringContent)
            .collect(Collectors.toList()))
            .containsExactlyInAnyOrder("http://www.w3.org/2009/xmlenc11#aes256-gcm", "http://www.w3.org/2009/xmlenc11#aes256-gcm");
    }
}