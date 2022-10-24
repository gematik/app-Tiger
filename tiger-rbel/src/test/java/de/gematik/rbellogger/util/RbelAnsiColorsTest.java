/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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