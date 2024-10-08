/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RbelX509ConverterTest {

  private RbelElement xmlMessage;

  @BeforeEach
  public void setUp() throws IOException {
    xmlMessage =
        RbelLogger.build(
                RbelConfiguration.builder().activateRbelParsingFor(List.of("X509")).build())
            .getRbelConverter()
            .parseMessage(
                readCurlFromFileWithCorrectedLineBreaks(
                        "src/test/resources/sampleMessages/xmlMessage.curl")
                    .getBytes(),
                null,
                null,
                Optional.of(ZonedDateTime.now()));
  }

  @SneakyThrows
  @Test
  void shouldRenderCleanHtml() {
    assertThat(RbelHtmlRenderer.render(List.of(xmlMessage))).isNotBlank();
  }

  @SneakyThrows
  @Test
  void shouldBeAccessibleViaRbelPath() {
    final RbelElement certificateElement =
        xmlMessage.findElement("$..[?(@.subject=~'.*TEST-ONLY.*')]").get();

    assertThat(certificateElement)
        .isEqualTo(
            xmlMessage
                .findElement(
                    "$.body.RegistryResponse.RegistryErrorList.RegistryError.jwtTag.text.header.x5c.0.content")
                .get());
  }

  @SneakyThrows
  @Test
  void shouldParseX500ContentAsWell() {
    assertThat(xmlMessage.findElement("$..subject.CN").get().getRawStringContent())
        .isEqualTo("IDP Sig 3");
  }
}
