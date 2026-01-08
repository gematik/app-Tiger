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
package de.gematik.test.tiger.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TigerVersionProviderTest {

  @Test
  void getTigerVersionString_shouldReturnNonEmptyVersion() {
    String version = TigerVersionProvider.getTigerVersionString();

    assertThat(version).isNotNull().isNotEmpty().doesNotContain("null");
  }

  @Test
  void getTigerVersionString_shouldContainVersionFormat() {
    String version = TigerVersionProvider.getTigerVersionString();

    assertThat(version)
        .as("Version should contain semantic version pattern (e.g., 4.1.12)")
        .matches(".*\\d+\\.\\d+\\.\\d+.*");
  }

  @Test
  void getTigerVersionString_shouldBeConsistent() {
    String version1 = TigerVersionProvider.getTigerVersionString();
    String version2 = TigerVersionProvider.getTigerVersionString();

    assertThat(version1).isEqualTo(version2);
  }
}
