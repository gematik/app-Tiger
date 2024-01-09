/*
 * Copyright (c) 2024 gematik GmbH
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

package de.gematik.test.tiger.common.banner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.dtmo.jfiglet.FigFontResources;
import de.gematik.rbellogger.util.RbelAnsiColors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TestBanner {

  private static final String REGEX_MATCH_ASCII_COLORS = "\\x1b|\\[|[\\d;]|m|\\n{2,}|={5,}";

  @SuppressWarnings("SpellCheckingInspection")
  private static Stream<Arguments> provideMessageForCheckingSymbols() {
    return Stream.of(
        Arguments.of(
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZÄäÜüÖöß?%&12345,67;89:0<>!$#"));
  }

  @ParameterizedTest
  @MethodSource("provideMessageForCheckingSymbols")
  void testAllSymbolsArePresentInBannerMessage(String message) {
    Banner.setFont(FigFontResources.BUBBLE_FLF);
    assertEachSymbolIsPresent(message);

    Banner.setFont(FigFontResources.LEAN_FLF);
    assertEachSymbolIsPresent(message);

    Banner.setFont(FigFontResources.SMSHADOW_FLF);
    assertEachSymbolIsPresent(message);

    Banner.setFont(FigFontResources.BANNER_FLF);
    assertEachSymbolIsPresent(message);
  }

  @Test
  void testBannerStrRedOK() {
    Banner.setFont(FigFontResources.STANDARD_FLF);

    var colors = new String[] {"roten", "rot", "RED"};
    for (String col : colors) {
      String str = Banner.toBannerStr("TestString", RbelAnsiColors.seekColor(col).toString());
      assertThat(str)
          .isEqualTo(
              "\u001B[0;31m"
                  + StringUtils.repeat('=', 120)
                  + "\u001B[0m\n"
                  + "\u001B[0;31m  _____         _   ____  _        _             \u001B[0m\n"
                  + "\u001B[0;31m |_   _|__  ___| |_/ ___|| |_ _ __(_)_ __   __ _ \u001B[0m\n"
                  + "\u001B[0;31m   | |/ _ \\/ __| __\\___ \\| __| '__| | '_ \\ / _` |\u001B[0m\n"
                  + "\u001B[0;31m   | |  __/\\__ \\ |_ ___) | |_| |  | | | | | (_| |\u001B[0m\n"
                  + "\u001B[0;31m   |_|\\___||___/\\__|____/ \\__|_|  |_|_| |_|\\__, |\u001B[0m\n"
                  + "\u001B[0;31m                                           |___/ \u001B[0m\n"
                  + "\u001B[0;31m"
                  + StringUtils.repeat('=', 120)
                  + "\u001B[0m");
    }
  }

  @Test
  void testBannerStrCOLOROK() {
    Banner.setFont(FigFontResources.STANDARD_FLF);
    var colors = new String[] {"roten", "rot", "RED"};
    for (String col : colors) {
      String str = Banner.toBannerStrWithCOLOR("TestString", col);
      assertThat(str)
          .isEqualTo(
              "\u001B[0;31m"
                  + StringUtils.repeat('=', 120)
                  + "\u001B[0m\n"
                  + "\u001B[0;31m  _____         _   ____  _        _             \u001B[0m\n"
                  + "\u001B[0;31m |_   _|__  ___| |_/ ___|| |_ _ __(_)_ __   __ _ \u001B[0m\n"
                  + "\u001B[0;31m   | |/ _ \\/ __| __\\___ \\| __| '__| | '_ \\ / _` |\u001B[0m\n"
                  + "\u001B[0;31m   | |  __/\\__ \\ |_ ___) | |_| |  | | | | | (_| |\u001B[0m\n"
                  + "\u001B[0;31m   |_|\\___||___/\\__|____/ \\__|_|  |_|_| |_|\\__, |\u001B[0m\n"
                  + "\u001B[0;31m                                           |___/ \u001B[0m\n"
                  + "\u001B[0;31m"
                  + StringUtils.repeat('=', 120)
                  + "\u001B[0m");
    }
  }

  @SuppressWarnings("SpellCheckingInspection")
  @Test
  void testBannerStrCOLORInvalidColorName() {
    assertThatThrownBy(() -> Banner.toBannerStrWithCOLOR("TestString", "NOCOLORKNOWN"))
        .isInstanceOf(RuntimeException.class);
  }

  private static void assertEachSymbolIsPresent(String msg) {
    // DEBUG Arrays.stream(Banner.toBannerStr(msg,
    // RbelAnsiColors.GREEN_BOLD.toString()).split("\n")).forEach(System.out::println);
    for (int i = 0; i < msg.length(); i++) {
      String initialSymbol =
          Banner.toBannerStr(msg.substring(i, i + 1), RbelAnsiColors.GREEN_BOLD.toString());

      String updatedSymbol = initialSymbol.replaceAll(REGEX_MATCH_ASCII_COLORS, "");
      assertThat(StringUtils.isBlank(updatedSymbol))
          .withFailMessage("The symbol " + msg.charAt(i) + " is not displayed.")
          .isFalse();
    }
  }
}
