/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import java.util.List;
import lombok.Data;

@Data
public class TigerConfigurationKeyString {
    private final static List<Character> FORBIDDEN_CHARACTERS = List.of('{', '}', '|');

    private final String value;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof String) {
            return value.equalsIgnoreCase((String) o);
        }
        if (!(o instanceof TigerConfigurationKeyString)) return false;

        TigerConfigurationKeyString that = (TigerConfigurationKeyString) o;

        return value != null ? value.equalsIgnoreCase(that.value) : that.value == null;
    }

    @Override
    public int hashCode() {
        return value != null ? value.toLowerCase().hashCode() : 0;
    }

    public static TigerConfigurationKeyString wrapAsKey(String value){
        for (Character c : FORBIDDEN_CHARACTERS) {
            value = value.replace(c, '_');
        }
        return new TigerConfigurationKeyString(value);
    }

    public String asLowerCase() {
        return value.toLowerCase();
    }

    public String asString() {
        return value;
    }
}
