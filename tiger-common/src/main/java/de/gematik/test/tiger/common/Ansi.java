package de.gematik.test.tiger.common;

public interface Ansi {

    String RESET = "\u001B[0m";

    String BOLD = "\u001B[1m";
    String DIM = "\u001B[2m";
    String ITALIC = "\u001B[3m";
    String UNDERLINE = "\u001B[4m";
    String BLINK = "\u001B[5m";
    String FAST_BLINK = "\u001B[6m";
    String REVERSE = "\u001B[7m";
    String INVISIBLE = "\u001B[8m";

    String BLACK = "\u001B[30m";
    String RED = "\u001B[31m";
    String GREEN = "\u001B[32m";
    String YELLOW = "\u001B[33m";
    String BLUE = "\u001B[34m";
    String MAGENTA = "\u001B[35m";
    String CYAN = "\u001B[36m";
    String WHITE = "\u001B[37m";

    String BGBLACK = "\u001B[40m";
    String BGRED = "\u001B[41m";
    String BGGREEN = "\u001B[42m";
    String BGYELLOW = "\u001B[43m";
    String BGBLUE = "\u001B[44m";
    String BGMAGENTA = "\u001B[45m";
    String BGCYAN = "\u001B[46m";
    String BGWHITE = "\u001B[47m";

}
