/*
 * Copyright (c) 2021 gematik GmbH
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

package de.gematik.test.tiger.common;

public final class Ansi {

    public final static String RESET = "\u001B[0m";

    public final static String BOLD = "\u001B[1m";
    public final static String DIM = "\u001B[2m";
    public final static String ITALIC = "\u001B[3m";
    public final static String UNDERLINE = "\u001B[4m";
    public final static String BLINK = "\u001B[5m";
    public final static String FAST_BLINK = "\u001B[6m";
    public final static String REVERSE = "\u001B[7m";
    public final static String INVISIBLE = "\u001B[8m";

    public final static String BLACK = "\u001B[30m";
    public final static String RED = "\u001B[31m";
    public final static String GREEN = "\u001B[32m";
    public final static String YELLOW = "\u001B[33m";
    public final static String BLUE = "\u001B[34m";
    public final static String MAGENTA = "\u001B[35m";
    public final static String CYAN = "\u001B[36m";
    public final static String WHITE = "\u001B[37m";

    public final static String BGBLACK = "\u001B[40m";
    public final static String BGRED = "\u001B[41m";
    public final static String BGGREEN = "\u001B[42m";
    public final static String BGYELLOW = "\u001B[43m";
    public final static String BGBLUE = "\u001B[44m";
    public final static String BGMAGENTA = "\u001B[45m";
    public final static String BGCYAN = "\u001B[46m";
    public final static String BGWHITE = "\u001B[47m";

    public static String colorize(String msg, String ansiCols) {
        return ansiCols + msg + Ansi.RESET;
    }
}
