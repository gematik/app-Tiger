/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.banner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.gematik.rbellogger.util.RbelAnsiColors;
import org.junit.Test;

public class TestBanner {
    @Test
    public void testBannerShoutOK() { //NOSONAR
        Banner.shout("TIGER Director V1.2.0", RbelAnsiColors.GREEN_BOLD.toString());
        Banner.shout("Starting director...", RbelAnsiColors.GREEN.toString());
        Banner.shout("abcdefghijklmnopqrstuvwxyz");
        Banner.shout("ÄäÜüÖöß?%&");
        Banner.shout("12345,67;89:0");
    }

    @Test public void testBannerStrRedOK() {
        var colors = new String[] { "roten", "rot"}; // TODO ET, "RED"};
        for (String col: colors) {
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
        var colors = new String[] { "roten", "rot"}; // TODO ET, "RED"};
        for (String col: colors) {
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

    @Test public void testBannerStrCOLORInvalidColorName() {
        assertThatThrownBy(() -> {
            String str = Banner.toBannerStrWithCOLOR("TestString", "NOCOLORKNOWN");
        }).isInstanceOf(RuntimeException.class);
    }
}
