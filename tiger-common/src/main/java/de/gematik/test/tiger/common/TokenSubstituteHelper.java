/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TokenSubstituteHelper {

    public static String substitute(String str, final String token, final Map<String, Object> valueMap) {
        final String tokenStr = "${" + (token.isBlank() ? "" : token + ".");
        int varIdx = str.indexOf(tokenStr);
        while (varIdx != -1) {
            final int endVar = str.indexOf('}', varIdx);
            final String varName = str.substring(varIdx + tokenStr.length(), endVar);
            if (valueMap.get(varName) != null) {
                str = str.substring(0, varIdx) + valueMap.get(varName) + str.substring(endVar + 1);
                varIdx = str.indexOf(tokenStr);
            } else {
                varIdx = str.indexOf(tokenStr, varIdx + 1);
            }
        }
        return str;
    }

    public static String substitute(String str) {
        final String tokenStr = "${";
        int varIdx = str.indexOf(tokenStr);
        while (varIdx != -1) {
            final int endVar = str.indexOf('}', varIdx);
            final String varName = str.substring(varIdx + tokenStr.length(), endVar);
            final Optional<String> valueOptional = TigerGlobalConfiguration.readStringOptional(varName);
            if (valueOptional.isPresent()) {
                str = str.substring(0, varIdx) + valueOptional.get() + str.substring(endVar + 1);
                varIdx = str.indexOf(tokenStr);
            } else {
                varIdx = str.indexOf(tokenStr, varIdx + 1);
            }
        }
        return str;
    }
}
