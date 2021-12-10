/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.banner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.gematik.rbellogger.util.RbelAnsiColors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TestBanner {

    private static final String REGEX_MATCH_ASCII_COLORS = "\\x1b|\\[|[0-9;]|[m]|[\\n]{2,}|[=]{5,}";

    private static Stream<Arguments> provideMessagesForBannerShout() {
        return Stream.of(
            Arguments.of("TIGER Director V1.2.0"),
            Arguments.of("Starting director..."),
            Arguments.of("abcdefghijklmnopqrstuvwxyzÄäÜüÖöß?%&12345,67;89:0<>!$#")
        );
    }

    private static Stream<Arguments> provideMessageForCheckingSymbols() {
        return Stream.of(
            Arguments.of("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZÄäÜüÖöß?%&12345,67;89:0<>!$#")
        );
    }

    @ParameterizedTest
    @MethodSource("provideMessagesForBannerShout")
    public void testBannerMessageShoutForAllFonts(String message) {
        changeBannerFontTo("Straight");
        Banner.shout(message, RbelAnsiColors.GREEN_BOLD.toString());

        changeBannerFontTo("Thin");
        Banner.shout(message, RbelAnsiColors.GREEN_BOLD.toString());

        changeBannerFontTo("Doom");
        Banner.shout(message, RbelAnsiColors.GREEN_BOLD.toString());

        changeBannerFontTo("Spliff");
        Banner.shout(message, RbelAnsiColors.GREEN_BOLD.toString());
    }


    @ParameterizedTest
    @MethodSource("provideMessageForCheckingSymbols")
    public void testAllSymbolsArePresentInBannerMessage(String message) {
        changeBannerFontTo("Straight");
        assertEachSymbolIsPresent(message);

        changeBannerFontTo("Thin");
        assertEachSymbolIsPresent(message);

        changeBannerFontTo("Doom");
        assertEachSymbolIsPresent(message);

        changeBannerFontTo("Spliff");
        assertEachSymbolIsPresent(message);
    }

    @Test
    public void testBannerStrRedOK() {
        changeBannerFontTo("Straight");

        var colors = new String[]{"roten", "rot", "RED"};
        for (String col : colors) {
            String str = Banner.toBannerStr("TestString", RbelAnsiColors.seekColor(col).toString());
            assertThat(str).isEqualTo(
                "\u001B[0;31m====================================================================================================\u001B[0m\n"
                    + "\u001B[0;31m ___               __                     \u001B[0m\n"
                    + "\u001B[0;31m  |    _   _  |_  (_   |_   _ .   _    _  \u001B[0m\n"
                    + "\u001B[0;31m  |   (=  _)  |_  __)  |_  |  |  | )  (_) \u001B[0m\n"
                    + "\u001B[0;31m                                      _/  \u001B[0m\n"
                    + "\u001B[0;31m====================================================================================================\u001B[0m");
        }
    }

    @Test
    public void testBannerStrCOLOROK() {
        System.setProperty("TIGER_BANNER_FONT", "Straight");
        Banner.initialize();
        var colors = new String[]{"roten", "rot", "RED"};
        for (String col : colors) {
            String str = Banner.toBannerStrWithCOLOR("TestString", col);
            assertThat(str).isEqualTo(
                "\u001B[0;31m====================================================================================================\u001B[0m\n"
                    + "\u001B[0;31m ___               __                     \u001B[0m\n"
                    + "\u001B[0;31m  |    _   _  |_  (_   |_   _ .   _    _  \u001B[0m\n"
                    + "\u001B[0;31m  |   (=  _)  |_  __)  |_  |  |  | )  (_) \u001B[0m\n"
                    + "\u001B[0;31m                                      _/  \u001B[0m\n"
                    + "\u001B[0;31m====================================================================================================\u001B[0m");
        }
    }

    @Test
    public void testBannerStrCOLORInvalidColorName() {
        assertThatThrownBy(() -> Banner.toBannerStrWithCOLOR("TestString", "NOCOLORKNOWN")).isInstanceOf(
            RuntimeException.class);
    }

    private static void assertEachSymbolIsPresent(String msg) {
        for (int i = 0; i < msg.length(); i++) {
            String initialSymbol = Banner.toBannerStr(msg.substring(i, i + 1), RbelAnsiColors.GREEN_BOLD.toString());

            String updatedSymbol = initialSymbol.replaceAll(REGEX_MATCH_ASCII_COLORS, "");
            assertThat(StringUtils.isBlank(updatedSymbol))
                .withFailMessage("The symbol " + msg.charAt(i) + " is not displayed.")
                .isFalse();
        }
    }

    private static void changeBannerFontTo(String fontName) {
        System.setProperty("TIGER_BANNER_FONT", fontName);

        Banner.initialize();
    }
}
