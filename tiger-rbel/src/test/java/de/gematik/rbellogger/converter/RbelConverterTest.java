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

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelElementConvertionPair;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
            (el, c) -> {
              if (el.hasFacet(RbelJwtFacet.class)) {
                throw new RuntimeException("this exception should be ignored");
              }
            });

    var convertedMessage =
        rbelLogger
            .getRbelConverter()
            .parseMessage(curlMessage.getBytes(), null, null, Optional.of(ZonedDateTime.now()));

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
    final Optional<ZonedDateTime> now = Optional.of(ZonedDateTime.now());

    assertThatThrownBy(() -> rbelConverter.parseMessage((byte[]) null, null, null, now))
        .isInstanceOf(RbelConversionException.class);
  }

  @Test
  void simulateRaceCondition_PairingShouldBeConserved() {
    RbelElement pair1A = new RbelElement("foo".getBytes(), null);
    RbelElement pair1B = new RbelElement("foo".getBytes(), null);
    RbelElement pair2A = new RbelElement("foo".getBytes(), null);
    RbelElement pair2B = new RbelElement("foo".getBytes(), null);

    pair1A.addFacet(new RbelHttpRequestFacet(null, null, null));
    pair2A.addFacet(new RbelHttpRequestFacet(null, null, null));
    pair1B.addFacet(new RbelHttpResponseFacet(null, null, pair1A));
    pair2B.addFacet(new RbelHttpResponseFacet(null, null, pair2A));

    var rbelLogger = RbelLogger.build();
    rbelLogger
        .getRbelConverter()
        .parseMessage(
            new RbelElementConvertionPair(pair1A), null, null, Optional.of(ZonedDateTime.now()));
    rbelLogger
        .getRbelConverter()
        .parseMessage(
            new RbelElementConvertionPair(pair1A, CompletableFuture.completedFuture(pair1B)),
            null,
            null,
            Optional.of(ZonedDateTime.now()));
    rbelLogger
        .getRbelConverter()
        .parseMessage(
            new RbelElementConvertionPair(pair2A), null, null, Optional.of(ZonedDateTime.now()));
    rbelLogger
        .getRbelConverter()
        .parseMessage(
            new RbelElementConvertionPair(pair2A, CompletableFuture.completedFuture(pair2B)),
            null,
            null,
            Optional.of(ZonedDateTime.now()));

    assertThat(pair1B.getFacetOrFail(RbelHttpResponseFacet.class).getRequest()).isEqualTo(pair1A);
    assertThat(pair2B.getFacetOrFail(RbelHttpResponseFacet.class).getRequest()).isEqualTo(pair2A);
  }
}
