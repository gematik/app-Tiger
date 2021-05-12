package de.gematik.test.tiger.common.banner;

import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.OSEnvironment;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

// TODO support german umlauts

public class Banner {

    private static Map<Character, List<String>> asciiArt = null;

    private static final Map<String, BannerConfig> configs = new HashMap<>();

    private static BannerConfig cfg;

    @SneakyThrows
    private static void initialize() {
        configs.put("Spliff", new BannerConfig(9, 5, true));
        configs.put("Doom", new BannerConfig(12, 8, true));
        configs.put("Thin", new BannerConfig(6, 6, false));
        configs.put("Straight", new BannerConfig(6, 4, false));

        String font = OSEnvironment.getAsString("TIGER_BANNER_FONT", "Straight");
        cfg = configs.get(font);

        asciiArt = new HashMap<>();
        List<String> lines = IOUtils
            .readLines(Objects.requireNonNull(Banner.class.getResourceAsStream(
                "/de/gematik/test/tiger/common/banner/ascii-" + font + ".txt")),
                StandardCharsets.UTF_8);
        for (int ascii = (int) ' '; ascii < 'ü'; ascii++) {
            List<String> linesForChar = new ArrayList<>();
            int maxWidth = 0;
            for (int i = 0; i < cfg.height; i++) {
                maxWidth = Math.max(lines.get((ascii - ' ' + 1) * cfg.height + i).trim().length(), maxWidth);
            }
            if (maxWidth < cfg.width) {
                maxWidth++;
            }

            for (int i = 0; i < cfg.height; i++) {
                String line = lines.get((ascii - ' ' + 1) * cfg.height + i);
                linesForChar.add(line.substring(0, Math.min(maxWidth, line.length())));
            }
            asciiArt.put((char) ascii, linesForChar);
        }
    }

    public static String toBannerStr(String msg, String ansiColors) {
        return ansiColors + StringUtils.repeat('=', 100) + Ansi.RESET + "\n"
            + toBannerLines(msg).stream()
            .map(line -> ansiColors + line + Ansi.RESET)
            .collect(Collectors.joining("\n"))
            + "\n" + ansiColors + StringUtils.repeat('=', 100) + Ansi.RESET;
    }

    public static String toBannerStrWithCOLOR(String msg, String colorName) {
        try {
            final String ansiColors = (String) Ansi.class.getDeclaredField(colorName).get(null);

            return ansiColors + StringUtils.repeat('=', 100) + Ansi.RESET + "\n"
                + toBannerLines(msg).stream()
                .map(line -> ansiColors + line + Ansi.RESET)
                .collect(Collectors.joining("\n"))
                + "\n" + ansiColors + StringUtils.repeat('=', 100) + Ansi.RESET;
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new AssertionError("Unknown color name '" + colorName + "'!", e);
        }
    }

    private static List<String> toBannerLines(String msg) {
        if (asciiArt == null) {
            initialize();
        }
        List<String> outLines = new ArrayList<>();
        for (int y = 0; y < cfg.height; y++) {
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
        shout(msg, Ansi.BOLD + Ansi.YELLOW);
    }

    public static void shout(String msg, String ansiColors) {
        toBannerLines(msg).forEach(line -> System.out.println(ansiColors + line + Ansi.RESET));
    }
}

// https://patorjk.com/software/taag/#p=display&f=Doom&t=

/*

!
"
#
$
%
&
'
(
)
*
+
,
-
.
/
0
1
2
3
4
5
6
7
8
9
:
;
<
=
>
?
@
A
B
C
D
E
F
G
H
I
J
K
L
M
N
O
P
Q
R
S
T
U
V
W
X
Y
Z
[
\
]
^
_
`
a
b
c
d
e
f
g
h
i
j
k
l
m
n
o
p
q
r
s
t
u
v
w
x
y
z
{
|
}
~




e
H
€

‚
ƒ
„
…
†
‡
ˆ
‰
Š
‹
Œ

Ž


‘
’
“
”
•
–
—
˜
™
š
›
œ

ž
Ÿ

¡
¢
£
¤
¥
¦
§
¨
©
ª
«
¬
­
®
¯
°
±
²
³
´
µ
¶
·
¸
¹
º
»
¼
½
¾
¿
À
Á
Â
Ã
Ä
Å
Æ
Ç
È
É
Ê
Ë
Ì
Í
Î
Ï
Ð
Ñ
Ò
Ó
Ô
Õ
Ö
×
Ø
Ù
Ú
Û
Ü
Ý
Þ
ß
à
á
â
ã
ä
å
æ
ç
è
é
ê
ë
ì
í
î
ï
ð
ñ
ò
ó
ô
õ
ö
÷
ø
ù
ú
û
ü
ý
þ
ÿ

 */