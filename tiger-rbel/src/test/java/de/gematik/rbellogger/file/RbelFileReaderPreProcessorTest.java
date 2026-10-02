/*
 *
 * Copyright 2026 gematik GmbH
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
package de.gematik.rbellogger.file;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.rbellogger.facets.timing.RbelMessageTimingFacet;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.val;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class RbelFileReaderPreProcessorTest {

  private static final ZonedDateTime COMPENSATED = ZonedDateTime.parse("2020-01-01T00:00:00Z");

  @Test
  void preProcessorMetadata_reachesTheTimingFacetTheHistoryPublishes() {
    val messages =
        read(
            (element, metadata) -> {
              element.addFacet(new RbelNoteFacet("marked before parsing"));
              metadata.withTransmissionTime(COMPENSATED);
            });

    assertThat(messages).hasSize(1);
    assertThat(
            messages
                .get(0)
                .getFacet(RbelMessageTimingFacet.class)
                .map(RbelMessageTimingFacet::getTransmissionTime))
        .as(
            "the timing facet is derived from the metadata while the message is being parsed, so a"
                + " transmission time set afterwards never reaches it - clock skew compensation has"
                + " to happen before the message is handed to the converter")
        .contains(COMPENSATED);
  }

  @Test
  void preProcessorFacets_areOnTheMessageBeforeItReachesTheHistory() {
    val messages = read((element, metadata) -> element.addFacet(new RbelNoteFacet("marked")));

    assertThat(messages).hasSize(1);
    assertThat(messages.get(0).getFacet(RbelNoteFacet.class).map(RbelNoteFacet::getValue))
        .as("a marker set by the pre-processor survives the conversion")
        .contains("marked");
  }

  private List<RbelElement> read(
      java.util.function.BiConsumer<RbelElement, de.gematik.rbellogger.data.RbelMessageMetadata>
          preProcessor) {
    val converter = RbelLogger.build().getRbelConverter();
    val reader = new RbelFileReader(converter);
    return reader.convertRbelFileEntries(
        Stream.of(tgrLine()), Optional.empty(), null, preProcessor);
  }

  private static String tgrLine() {
    val request = "GET /somewhere HTTP/1.1\r\nHost: server\r\nContent-Length: 0\r\n\r\n";
    return new JSONObject()
        .put(RbelFileWriter.MESSAGE_UUID, UUID.randomUUID().toString())
        .put(
            RbelFileWriter.RAW_MESSAGE_CONTENT,
            Base64.getEncoder().encodeToString(request.getBytes(StandardCharsets.UTF_8)))
        .toString();
  }
}
