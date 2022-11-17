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


import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.gematik.rbellogger.RbelOptions.ENABLE_ANSI_COLORS;

public enum RbelAnsiColors {

    RESET("\033[0m", "reset"),
    BLACK("\033[0;30m", "schwarz", "schwarzen", "black"),
    RED("\033[0;31m", "rot", "roten", "red"),
    GREEN("\033[0;32m", "grün", "grünen", "green"),
    YELLOW("\033[0;33m", "gelb", "gelben", "yellow"),
    BLUE("\033[0;34m", "blau", "blauen", "blue"),
    PURPLE("\033[0;35m", "magenta", "magentanen", "purple"),
    CYAN("\033[0;36m", "türkis", "türkisen", "cyan"),
    WHITE("\033[0;37m", "weiß", "weiss", "weißen", "weissen", "white"),
    BLACK_BOLD("\033[1;30m"),
    RED_BOLD("\033[1;31m"),
    GREEN_BOLD("\033[1;32m"),
    YELLOW_BOLD("\033[1;33m"),
    BLUE_BOLD("\033[1;34m"),
    PURPLE_BOLD("\033[1;35m"),
    CYAN_BOLD("\033[1;36m"),
    WHITE_BOLD("\033[1;37m"),
    BLACK_UNDERLINED("\033[4;30m"),
    RED_UNDERLINED("\033[4;31m"),
    GREEN_UNDERLINED("\033[4;32m"),
    YELLOW_UNDERLINED("\033[4;33m"),
    BLUE_UNDERLINED("\033[4;34m"),
    PURPLE_UNDERLINED("\033[4;35m"),
    CYAN_UNDERLINED("\033[4;36m"),
    WHITE_UNDERLINED("\033[4;37m"),
    BLACK_BACKGROUND("\033[40m"),
    RED_BACKGROUND("\033[41m"),
    GREEN_BACKGROUND("\033[42m"),
    YELLOW_BACKGROUND("\033[43m"),
    BLUE_BACKGROUND("\033[44m"),
    PURPLE_BACKGROUND("\033[45m"),
    CYAN_BACKGROUND("\033[46m"),
    WHITE_BACKGROUND("\033[47m"),
    BLACK_BRIGHT("\033[0;90m"),
    RED_BRIGHT("\033[0;91m"),
    GREEN_BRIGHT("\033[0;92m"),
    YELLOW_BRIGHT("\033[0;93m"),
    BLUE_BRIGHT("\033[0;94m"),
    PURPLE_BRIGHT("\033[0;95m"),
    CYAN_BRIGHT("\033[0;96m"),
    WHITE_BRIGHT("\033[0;97m"),
    BLACK_BOLD_BRIGHT("\033[1;90m"),
    RED_BOLD_BRIGHT("\033[1;91m"),
    GREEN_BOLD_BRIGHT("\033[1;92m"),
    YELLOW_BOLD_BRIGHT("\033[1;93m"),
    BLUE_BOLD_BRIGHT("\033[1;94m"),
    PURPLE_BOLD_BRIGHT("\033[1;95m"),
    CYAN_BOLD_BRIGHT("\033[1;96m"),
    WHITE_BOLD_BRIGHT("\033[1;97m"),
    BLACK_BACKGROUND_BRIGHT("\033[0;100m"),
    RED_BACKGROUND_BRIGHT("\033[0;101m"),
    GREEN_BACKGROUND_BRIGHT("\033[0;102m"),
    YELLOW_BACKGROUND_BRIGHT("\033[0;103m"),
    BLUE_BACKGROUND_BRIGHT("\033[0;104m"),
    PURPLE_BACKGROUND_BRIGHT("\033[0;105m"),
    CYAN_BACKGROUND_BRIGHT("\033[0;106m"),
    WHITE_BACKGROUND_BRIGHT("\033[0;107m");

    private final String value;
    private final List<String> otherNames;

    RbelAnsiColors(String value, String... otherNames) {
        this.value = value;
        this.otherNames = Stream.of(otherNames)
            .map(String::toLowerCase)
            .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        if (ENABLE_ANSI_COLORS) {
            return value;
        } else {
            return "";
        }
    }

    public static Optional<RbelAnsiColors> seekColorSafe(String query) {
        return Arrays.stream(values())
            .filter(candidate -> candidate.doesColorMatchForQueryString(query))
            .findFirst();
    }

    public static RbelAnsiColors seekColor(String query) {
        return seekColorSafe(query)
            .orElseThrow(() -> new RbelUnkownAnsiColorException("Could not match string '"+query+"' to any known color"));
    }

    public boolean doesColorMatchForQueryString(String query) {
        return otherNames.contains(query.toLowerCase());
    }

    private static class RbelUnkownAnsiColorException extends RuntimeException {
        public RbelUnkownAnsiColorException(String s) {
            super(s);
        }
    }
}
