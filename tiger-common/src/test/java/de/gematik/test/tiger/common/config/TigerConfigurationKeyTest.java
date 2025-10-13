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
package de.gematik.test.tiger.common.config;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TigerConfigurationKeyTest {
  @Test
  void testCreateConfigurationKeyWithRepeatedSubKey_ShouldAppendToKey() {
    var baseKey =
        new TigerConfigurationKey("mockResponses", "testResponse", "nestedResponses", "login");

    assertThat(baseKey.downsampleKey())
        .isEqualTo("mockresponses.testresponse.nestedresponses.login");

    var combinedKey = new TigerConfigurationKey(baseKey, "nestedResponses");

    assertThat(combinedKey.downsampleKey())
        .isEqualTo("mockresponses.testresponse.nestedresponses.login.nestedresponses");
  }

  @ParameterizedTest
  @CsvSource({
    "PS_KOBURL, PS_KOBURL",
    "PS_KOBURL, ps.koburl",
    "PS_KOBURL, ps.kobUrl",
    "ps.koburl, ps.kobUrl"
  })
  void testDifferentKeys_shouldMatch(String value1, String value2) {
    assertThat(new TigerConfigurationKey(value1)).isEqualTo(new TigerConfigurationKey(value2));
  }
}
