/*
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */

package io.cucumber.junit;

import static io.cucumber.core.options.Constants.GLUE_PROPERTY_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

class TigerCucumberRunnerTest {

  @Test
  void testCommaSeparatedGlueCodeConversion() {
    assertThatNoException()
        .isThrownBy(
            () ->
                TigerCucumberRunner.parseCommandLineOptions(
                    new String[] {"--tiger.glues", "a,b,c"}));
  }

  @Test
  void normalizeTigerGluesArguments_replacesCommaSeparatedTigerGluesWithRepeatedGlueArguments() {
    assertThat(
            TigerCucumberRunner.parseTigerGlues(
                new String[] {
                  "--plugin", "pretty", "--tiger.glues", "a,b,c", "classpath:features"
                }))
        .containsExactly(
            "--plugin",
            "pretty",
            "--glue",
            "a",
            "--glue",
            "b",
            "--glue",
            "c",
            "classpath:features");
  }

  @Test
  void parseCommandLineOptions_convertsTigerGluesIntoCucumberGlueConfiguration() {
    assertThat(TigerCucumberRunner.parseCommandLineOptions(new String[] {"--tiger.glues", "a,b,c"}))
        .containsEntry(GLUE_PROPERTY_NAME, "classpath:/a,classpath:/b,classpath:/c");
  }
}
