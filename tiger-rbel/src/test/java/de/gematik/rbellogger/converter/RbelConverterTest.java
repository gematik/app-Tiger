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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.facets.jose.RbelJwtFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

class RbelConverterTest {

  @Test
  void errorDuringConversion_shouldBeIgnored() throws IOException {
    final String curlMessage =
        readCurlFromFileWithCorrectedLineBreaks(
            "src/test/resources/sampleMessages/jwtMessage.curl");

    var rbelLogger = RbelLogger.build();
    rbelLogger
        .getRbelConverter()
        .addConverter(
            RbelConverterPlugin.createPlugin(
                (el, c) -> {
                  if (el.hasFacet(RbelJwtFacet.class)) {
                    throw new RuntimeException("this exception should be ignored");
                  }
                }));

    var convertedMessage =
        rbelLogger
            .getRbelConverter()
            .parseMessage(curlMessage.getBytes(), new RbelMessageMetadata());

    FileUtils.writeStringToFile(
        new File("target/error.html"),
        new RbelHtmlRenderer().doRender(rbelLogger.getMessageHistory()),
        Charset.defaultCharset());

    assertThat(
            convertedMessage
                .findElement("$.body")
                .get()
                .getFacet(RbelNoteFacet.class)
                .get()
                .getValue())
        .contains("this exception should be ignored", this.getClass().getSimpleName());
  }

  @Test
  void parseMessage_shouldFailBecauseContentIsNull() {
    final RbelConverter rbelConverter = RbelLogger.build().getRbelConverter();

    assertThatThrownBy(
            () ->
                rbelConverter.parseMessage(
                    (byte[]) null,
                    new RbelMessageMetadata().withTransmissionTime(ZonedDateTime.now())))
        .isInstanceOf(RbelConversionException.class);
  }

  @Test
  void simulateRaceCondition_PairingShouldBeConserved() {
    RbelElement pair1A = new RbelElement("foo".getBytes(), null);
    RbelElement pair1B = new RbelElement("foo".getBytes(), null);
    RbelElement pair2A = new RbelElement("foo".getBytes(), null);
    RbelElement pair2B = new RbelElement("foo".getBytes(), null);

    pair1A.addFacet(new TracingMessagePairFacet(null, null));
    pair2A.addFacet(new TracingMessagePairFacet(null, null));
    pair1B.addFacet(new TracingMessagePairFacet(null, pair1A));
    pair2B.addFacet(new TracingMessagePairFacet(null, pair2A));

    var rbelLogger = RbelLogger.build();
    rbelLogger.getRbelConverter().parseMessage(pair1A, new RbelMessageMetadata());
    rbelLogger.getRbelConverter().parseMessage(pair1B, new RbelMessageMetadata());
    rbelLogger.getRbelConverter().parseMessage(pair2A, new RbelMessageMetadata());
    rbelLogger.getRbelConverter().parseMessage(pair2B, new RbelMessageMetadata());

    assertThat(pair1B.getFacetOrFail(TracingMessagePairFacet.class).getRequest()).isEqualTo(pair1A);
    assertThat(pair2B.getFacetOrFail(TracingMessagePairFacet.class).getRequest()).isEqualTo(pair2A);
  }
}
