/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.parser.model.gherkin;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Tag {

    private String name;
    private String parameter;

    public String getTagString() {
        if (!parameter.isBlank()) {
            return name + ":" + parameter;
        } else {
            return name;
        }
    }

    public static Tag fromString(final String s) {
        final int colon = s.indexOf(":");
        if (colon == -1) {
            return new Tag(s, "");
        } else {
            return new Tag(s.substring(0, colon), s.substring(colon + 1));
        }
    }
}
