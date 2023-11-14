/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.util;

import static de.gematik.rbellogger.util.RbelAnsiColors.WHITE;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    RbelAnsiColors.deactivateAnsiColors();

    assertThat(WHITE.toString()).isEmpty();

    RbelAnsiColors.activateAnsiColors();

    assertThat(WHITE.toString()).isNotEmpty();
  }
}
