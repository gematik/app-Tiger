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

package de.gematik.test.tiger.common.banner;

import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

/* do editing of the font files in the notepad
 while editing, missing spaces after the characters can occur and should be fixed*/

public class Banner {

    private Banner() {
    }

    private static Map<Character, List<String>> asciiArt = null;

    private static final Map<String, BannerFontMetrics> configs = new HashMap<>();

    private static BannerFontMetrics cfg;

    @SneakyThrows
    public static void initialize() {
        configs.put("Spliff", new BannerFontMetrics(9, 5, true));
        configs.put("Doom", new BannerFontMetrics(12, 8, true));
        configs.put("Thin", new BannerFontMetrics(6, 6, false));
        configs.put("Straight", new BannerFontMetrics(6, 4, false));

        String font = TigerGlobalConfiguration.readString("TIGER_BANNER_FONT", "Straight");
        cfg = configs.get(font);

        asciiArt = new HashMap<>();
        List<String> lines = IOUtils
            .readLines(Objects.requireNonNull(Banner.class.getResourceAsStream(
                    "/de/gematik/test/tiger/common/banner/ascii-" + font + ".txt")),
                StandardCharsets.UTF_8);
        for (int ascii = ' '; ascii <= 'ü'; ascii++) {
            List<String> linesForChar = new ArrayList<>();
            int maxWidth = 0;
            for (int i = 0; i < cfg.getHeight(); i++) {
                maxWidth = Math.max(lines.get((ascii - ' ' + 1) * cfg.getHeight() + i).trim().length(), maxWidth);
            }
            if (maxWidth < cfg.getWidth()) {
                maxWidth++;
            }

            for (int i = 0; i < cfg.getHeight(); i++) {
                String line = lines.get((ascii - ' ' + 1) * cfg.getHeight() + i);
                linesForChar.add(line.substring(0, Math.min(maxWidth, line.length())));
            }
            asciiArt.put((char) ascii, linesForChar);
        }
    }

    public static String toBannerStr(String msg, String ansiColors) {
        return ansiColors + StringUtils.repeat('=', 100) + RbelAnsiColors.RESET + "\n"
            + toBannerLines(msg).stream()
            .map(line -> Ansi.colorize(line, ansiColors))
            .collect(Collectors.joining("\n"))
            + "\n" + Ansi.colorize(StringUtils.repeat('=', 100), ansiColors);
    }

    public static String toTextStr(String msg, String colorName) {
        final String ansiColors = RbelAnsiColors.seekColor(colorName.toLowerCase()).toString();
        return Ansi.colorize(msg, ansiColors);
    }

    public static String toBannerStrWithCOLOR(String msg, String colorName) {
        final String ansiColors = RbelAnsiColors.seekColor(colorName.toLowerCase()).toString();

        return Ansi.colorize(StringUtils.repeat('=', 100),ansiColors) + "\n"
            + toBannerLines(msg).stream()
            .map(line -> Ansi.colorize(line,ansiColors))
            .collect(Collectors.joining("\n"))
            + "\n" + Ansi.colorize( StringUtils.repeat('=', 100) , ansiColors);
    }

    private static List<String> toBannerLines(String msg) {
        if (asciiArt == null) {
            initialize();
        }
        List<String> outLines = new ArrayList<>();
        for (int y = 0; y < cfg.getHeight(); y++) {
            StringBuilder outLine = new StringBuilder();
            for (int i = 0; i < msg.length(); i++) {
                char ascii = msg.charAt(i);
                if (asciiArt.get(ascii) == null) {
                    ascii = ' ';
                }
                outLine.append(" ").append(asciiArt.get(ascii).get(y));
            }
            outLines.add(outLine.toString());
        }
        return outLines;
    }

    public static void shout(String msg) {
        shout(msg, RbelAnsiColors.YELLOW_BOLD.toString());
    }

    public static void shout(String msg, String ansiColors) {
        toBannerLines(msg).forEach(line -> System.out.println(Ansi.colorize(line, ansiColors)));
    }
}

// https://patorjk.com/software/taag/#p=display&f=Doom&t=