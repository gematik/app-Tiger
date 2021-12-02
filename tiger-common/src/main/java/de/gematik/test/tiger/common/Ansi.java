/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common;

import de.gematik.rbellogger.util.RbelAnsiColors;

public class Ansi {
    public static String colorize(String text, String ansiColorCode) {
        return ansiColorCode + text + RbelAnsiColors.RESET;
    }

    public static String colorize(String text, RbelAnsiColors txtColor) {
        return txtColor + text + RbelAnsiColors.RESET;
    }
}
