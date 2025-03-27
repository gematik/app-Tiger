/*
 *
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

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.facet.RbelNoteFacet;
import lombok.val;
import org.junit.jupiter.api.Test;

class TestRbelExceptionHandling {

  private static final RbelConverterPlugin EXCEPTION_PRODUCING_CONVERTER =
      RbelConverterPlugin.createPlugin(
          (rbelElement, converter) -> {
            throw new RuntimeException("Test exception");
          });

  @Test
  void throwExceptionDuringParsing_expectCorrectData() {
    val rbelLogger = RbelLogger.build(new RbelConfiguration());
    rbelLogger.getRbelConverter().addConverter(EXCEPTION_PRODUCING_CONVERTER);
    val result = rbelLogger.getRbelConverter().convertElement("Test", null);

    assertThat(result)
        .extractFacet(RbelNoteFacet.class)
        .extracting("value")
        .asString()
        .startsWith(
            "Exception during conversion with plugin"
                + " 'de.gematik.rbellogger.converter.RbelConverterPlugin$1")
        .containsAnyOf("(java.lang.RuntimeException: Test exception)");
  }
}
