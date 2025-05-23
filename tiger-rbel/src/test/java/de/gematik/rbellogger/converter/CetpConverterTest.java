/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.rbellogger.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Files;
import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.facets.cetp.RbelCetpFacet;
import de.gematik.rbellogger.facets.xml.RbelXmlFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.util.List;
import lombok.SneakyThrows;
import org.bouncycastle.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CetpConverterTest {

  private static final RbelConverter rbelConverter = RbelLogger.build().getRbelConverter();
  private static byte[] cetpMessageAsBytes;

  @SneakyThrows
  @BeforeAll
  public static void parseMessage() {
    cetpMessageAsBytes =
        Files.toByteArray(new File("src/test/resources/sampleMessages/cetp_ti_down.bin"));
  }

  @Test
  void convertMessage_shouldGiveCetpFacet() {
    var convertedMessage =
        rbelConverter.parseMessage(cetpMessageAsBytes, new RbelMessageMetadata());
    assertThat(convertedMessage.hasFacet(RbelCetpFacet.class)).isTrue();
    assertThat(convertedMessage.findElement("$.body").get().hasFacet(RbelXmlFacet.class)).isTrue();
  }

  @Test
  void convertMessageWithMissingBytes_shouldNotGiveCetpFacet() {
    var convertedMessage =
        rbelConverter.parseMessage(
            Arrays.copyOfRange(cetpMessageAsBytes, 0, 50), new RbelMessageMetadata());
    assertThat(convertedMessage.hasFacet(RbelCetpFacet.class)).isFalse();
  }

  @Test
  void messageLengthShouldBeWrappedCorrectly() {
    var convertedMessage =
        rbelConverter.parseMessage(cetpMessageAsBytes, new RbelMessageMetadata());
    assertThat(convertedMessage.findElement("$.messageLength").get().seekValue(Integer.class))
        .get()
        .isEqualTo(286);
    assertThat(convertedMessage.findElement("$.messageLength").get().getRawContent())
        .isEqualTo(new byte[] {0, 0, 1, 30});
    assertThat(
            convertedMessage
                .getFacetOrFail(RbelCetpFacet.class)
                .getMessageLength()
                .seekValue(Integer.class)
                .get()
                .intValue())
        .isEqualTo(286);
  }

  @SneakyThrows
  @Test
  @SuppressWarnings("java:S2699")
  void checkRendering() {
    rbelConverter.parseMessage(cetpMessageAsBytes, new RbelMessageMetadata());
    RbelHtmlRenderer.render(rbelConverter.getMessageList());
  }

  @SneakyThrows
  @Test
  void shouldRenderCleanHtmlCetp2() {
    var convertedMessage =
        rbelConverter.parseMessage(cetpMessageAsBytes, new RbelMessageMetadata());
    assertThat(RbelHtmlRenderer.render(List.of(convertedMessage))).isNotBlank();
  }
}
