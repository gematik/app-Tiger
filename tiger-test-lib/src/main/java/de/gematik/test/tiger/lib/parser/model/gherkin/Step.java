/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.parser.model.gherkin;

import de.gematik.test.tiger.lib.parser.TestParserException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@AllArgsConstructor
@Getter
public class Step {

    public static final List<String> KEYWORDS = List.of(
        "When", "Given", "Then", "And", "But", "Examples",
        "Wenn", "Angenommen", "Gegeben sei", "Gegeben seien",
        "Dann", "Und", "Aber", "Beispiele"
    );

    private final String keyword;
    private final List<String> lines;

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public Step clone()  {
        return new Step(keyword, new ArrayList<>(lines));
    }

    public static Step fromLine(final String line) {
        final String kw = getKeyword(line.trim());
        return KEYWORDS.stream()
            .filter(keyword -> keyword.equals(kw))
            .map(keyword -> new Step(keyword, new ArrayList<>(List.of(line))))
            .findFirst()
            .orElseThrow(() -> new TestParserException("Unknown Step '" + kw + "'"));
    }

    public static String getKeyword(final String line) {
        if (line.startsWith("Example") || line.startsWith("Beispiele")) {
            final int colon = line.indexOf(":");
            if (colon != -1) {
                return line.substring(0, colon).replace(" ", "").replace("\t", "");
            }
        }
        return extractSingleOrMultiWordKeyword(line);
    }

    private static String extractSingleOrMultiWordKeyword(String line) {
        String firstWord = StringUtils.substringBefore(line, " ");
        if (firstWord.equals("Gegeben")) {
            final int space = line.indexOf(' ', firstWord.length()+1);
            if (space != -1) {
                return firstWord + line.substring(firstWord.length(), space);
            }
        }
        return firstWord;
    }
}
