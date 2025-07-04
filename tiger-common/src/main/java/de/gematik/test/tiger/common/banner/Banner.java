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
package de.gematik.test.tiger.common.banner;

import com.github.dtmo.jfiglet.FigFontResources;
import com.github.dtmo.jfiglet.FigletRenderer;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.exceptions.TigerOsException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/* do editing of the font files in the notepad
while editing, missing spaces after the characters can occur and should be fixed*/

public class Banner {

  private static FigletRenderer figletRenderer;

  private Banner() {}

  static {
    setFont(FigFontResources.STANDARD_FLF);
  }

  public static String toBannerStr(String msg, String ansiColors) {
    return ansiColors
        + StringUtils.repeat('=', 120)
        + RbelAnsiColors.RESET
        + "\n"
        + toBannerLines(msg).stream()
            .map(line -> Ansi.colorize(line, ansiColors))
            .collect(Collectors.joining("\n"))
        + "\n"
        + Ansi.colorize(StringUtils.repeat('=', 120), ansiColors);
  }

  public static String toTextStr(String msg, String colorName) {
    final String ansiColors = RbelAnsiColors.seekColor(colorName.toLowerCase()).toString();
    return Ansi.colorize(msg, ansiColors);
  }

  public static String toBannerStrWithCOLOR(String msg, String colorName) {
    final String ansiColors = RbelAnsiColors.seekColor(colorName.toLowerCase()).toString();

    return Ansi.colorize(StringUtils.repeat('=', 120), ansiColors)
        + "\n"
        + toBannerLines(msg).stream()
            .map(line -> Ansi.colorize(line, ansiColors))
            .collect(Collectors.joining("\n"))
        + "\n"
        + Ansi.colorize(StringUtils.repeat('=', 120), ansiColors);
  }

  private static List<String> toBannerLines(String msg) {
    return Arrays.asList(figletRenderer.renderText(msg).split("\n"));
  }

  public static void setFont(String fontName) {
    try {
      figletRenderer = new FigletRenderer(FigFontResources.loadFigFontResource(fontName));
    } catch (IOException ioe) {
      throw new TigerOsException("Unable to load font " + fontName, ioe);
    }
  }
}
