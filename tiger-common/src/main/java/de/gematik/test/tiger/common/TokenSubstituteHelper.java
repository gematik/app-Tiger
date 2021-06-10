package de.gematik.test.tiger.common;

import java.util.Map;

public class TokenSubstituteHelper {
    public static String substitute(String str, final String token, final Map<String, Object> valueMap) {
        final String tokenStr = "${" + (token.isBlank() ? "" : token + ".");
        int varIdx = str.indexOf(tokenStr);
        while (varIdx != -1) {
            final int endVar = str.indexOf("}", varIdx);
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
}
