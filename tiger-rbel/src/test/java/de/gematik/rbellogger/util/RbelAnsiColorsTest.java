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
package de.gematik.rbellogger.util;

import static de.gematik.rbellogger.util.RbelAnsiColors.WHITE;
import static org.assertj.core.api.Assertions.assertThat;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Slf4j
class RbelAnsiColorsTest {

  @Test
  @DisplayName("WHITE and weiß and weiss should have same toString value")
  void whiteAndWeissShouldMatch() {
    assertThat(WHITE)
        .isEqualTo(RbelAnsiColors.seekColor("wEiSs"))
        .isEqualTo(RbelAnsiColors.seekColor("Weiß"))
        .isEqualTo(RbelAnsiColors.seekColor("weiss"))
        .isEqualTo(RbelAnsiColors.seekColor("white"));
  }

  @Test
  @DisplayName("Should only give ANSI-values if global constant mandates it")
  void ansiOutputShouldHingeOnGlobalConstant() {
    RbelAnsiColors.deactivateAnsiColors();

    assertThat(WHITE.toString()).isEmpty();

    RbelAnsiColors.activateAnsiColors();

    assertThat(WHITE.toString()).isNotEmpty();
  }
}
