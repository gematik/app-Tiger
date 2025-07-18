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

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import de.gematik.rbellogger.facets.sicct.RbelSicctEnvelopeFacet;
import de.gematik.rbellogger.facets.sicct.SicctMessageType;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import lombok.val;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RbelSicctConverterTest {

  private RbelLogger rbelLogger;

  @BeforeEach
  void setUp() throws Exception {
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
    val requestSequenceNumber =
        rbelLogger
            .getMessageHistory()
            .getFirst()
            .getFacet(RbelSicctEnvelopeFacet.class)
            .map(RbelSicctEnvelopeFacet::getSequenceNumber)
            .flatMap(RbelElement::seekValue)
            .orElseThrow(() -> new IllegalStateException("No sequence number found"));
    assertThat(rbelLogger.getMessageHistory().getFirst())
        .hasGivenValueAtPosition("$.messageType", SicctMessageType.C_COMMAND)
        .hasContentEqualToAtPosition("$.srcOrDesAddress", new byte[] {0, 0})
        .hasContentEqualToAtPosition("$.sequenceNumber", new byte[] {1, 0x41})
        .hasGivenValueAtPosition("$.sequenceNumber", 0x141)
        .hasContentEqualToAtPosition("$.abRfu", new byte[] {0})
        .hasContentEqualToAtPosition("$.length", new byte[] {0, 0, 0, 0x0e})
        .extractFacet(TracingMessagePairFacet.class)
        .matches(pair -> pair.getRequest() == rbelLogger.getMessageHistory().getFirst())
        .extracting(TracingMessagePairFacet::getResponse)
        .matches(resp -> resp != rbelLogger.getMessageHistory().getFirst())
        .extracting(
            resp ->
                resp.getFacet(RbelSicctEnvelopeFacet.class)
                    .map(RbelSicctEnvelopeFacet::getSequenceNumber)
                    .flatMap(RbelElement::seekValue)
                    .orElseThrow())
        .matches(
            responseSeqNum -> Objects.equals(responseSeqNum, requestSequenceNumber),
            "sequence numbers should match, expected " + requestSequenceNumber);
  }
}
