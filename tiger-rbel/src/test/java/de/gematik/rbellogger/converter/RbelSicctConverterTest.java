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

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelSicctEnvelopeFacet;
import de.gematik.rbellogger.data.sicct.SicctMessageType;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RbelSicctConverterTest {

  private RbelLogger rbelLogger;

  @BeforeEach
  public void setUp() throws Exception {
    final RbelFileReaderCapturer fileReaderCapturer =
        RbelFileReaderCapturer.builder().rbelFile("src/test/resources/sicctTraffic.tgr").build();
    rbelLogger =
        RbelLogger.build(
            new RbelConfiguration().addCapturer(fileReaderCapturer).activateConversionFor("sicct"));
    fileReaderCapturer.initialize();
    fileReaderCapturer.close();
  }

  @Test
  void shouldRecognizeSicctMessages() throws IOException {
    FileUtils.writeStringToFile(
        new File("target/sicctFlow.html"), RbelHtmlRenderer.render(rbelLogger.getMessageHistory()));

    for (RbelElement msg : rbelLogger.getMessageHistory()) {
      assertThat(msg.hasFacet(RbelSicctEnvelopeFacet.class)).isTrue();
    }
  }

  @Test
  void testForBasicAttributesInSicctEnvelope() {
    assertThat(
            rbelLogger
                .getMessageHistory()
                .getFirst()
                .findElement("$.messageType")
                .get()
                .seekValue())
        .contains(SicctMessageType.C_COMMAND);
    assertThat(
            rbelLogger
                .getMessageHistory()
                .getFirst()
                .findElement("$.srcOrDesAddress")
                .get()
                .getRawContent())
        .isEqualTo(new byte[] {0, 0});
    assertThat(
            rbelLogger
                .getMessageHistory()
                .getFirst()
                .findElement("$.sequenceNumber")
                .get()
                .getRawContent())
        .isEqualTo(new byte[] {1, 0x41});
    assertThat(
            rbelLogger.getMessageHistory().getFirst().findElement("$.abRfu").get().getRawContent())
        .isEqualTo(new byte[] {0});
    assertThat(
            rbelLogger.getMessageHistory().getFirst().findElement("$.length").get().getRawContent())
        .isEqualTo(new byte[] {0, 0, 0, 0x0e});
  }
}
