/*
 *
 * Copyright 2021-2026 gematik GmbH
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

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.core.RbelValueFacet;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class PlainValueConversionTest {

  private static class RecordingPlugin extends RbelConverterPlugin {
    private final boolean wantsPlainValues;
    private final List<Object> values = new CopyOnWriteArrayList<>();

    private RecordingPlugin(boolean wantsPlainValues) {
      this.wantsPlainValues = wantsPlainValues;
    }

    @Override
    public RbelConversionPhase getPhase() {
      return RbelConversionPhase.CONTENT_PARSING;
    }

    @Override
    public boolean handlesPlainValues() {
      return wantsPlainValues;
    }

    @Override
    public void consumeElement(RbelElement element, RbelConversionExecutor converter) {
      element.getFacet(RbelValueFacet.class).map(RbelValueFacet::getValue).ifPresent(values::add);
    }
  }

  @Test
  void numbersAndBooleans_areOnlyOfferedToTheConvertersThatWantThem() {
    var wanting = new RecordingPlugin(true);
    var indifferent = new RecordingPlugin(false);
    var logger = RbelLogger.build();
    logger.getRbelConverter().addConverter(wanting);
    logger.getRbelConverter().addConverter(indifferent);
    var body = "{\"int\":1234,\"float\":1.5,\"flag\":true,\"text\":\"word\"}";
    var http =
        "POST /x HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nContent-Length: "
            + body.length()
            + "\r\n\r\n"
            + body;

    logger
        .getRbelConverter()
        .parseMessage(http.getBytes(StandardCharsets.UTF_8), new RbelMessageMetadata());

    assertThat(wanting.values).contains(1234L, 1.5, true, "word");
    assertThat(indifferent.values).contains("word").doesNotContain(1234L, 1.5, true);
  }
}
