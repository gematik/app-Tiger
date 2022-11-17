/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.util;

import de.gematik.rbellogger.RbelOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static de.gematik.rbellogger.util.RbelAnsiColors.WHITE;
import static org.assertj.core.api.Assertions.assertThat;

public class RbelAnsiColorsTest {

    @Test
    @DisplayName("WHITE and weiß and weiss should have same toString value")
    public void whiteAndWeissShouldMatch() {
        assertThat(WHITE)
            .isEqualTo(RbelAnsiColors.seekColor("wEiSs"))
            .isEqualTo(RbelAnsiColors.seekColor("Weiß"))
            .isEqualTo(RbelAnsiColors.seekColor("weiss"))
            .isEqualTo(RbelAnsiColors.seekColor("white"));
    }

    @Test
    @DisplayName("Should only give ANSI-values if global constant mandates it")
    public void ansiOutputShouldHingeOnGlobalConstant() {
        RbelOptions.deactivateAnsiColors();

        assertThat(WHITE.toString()).isEmpty();

        RbelOptions.activateAnsiColors();

        assertThat(WHITE.toString()).isNotEmpty();
    }
}
