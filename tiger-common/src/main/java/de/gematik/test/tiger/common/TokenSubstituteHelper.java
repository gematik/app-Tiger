/*
 * Copyright (c) 2023 gematik GmbH
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

import de.gematik.test.tiger.common.config.TigerConfigurationLoader;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import java.util.Optional;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TokenSubstituteHelper {

    public static String substitute(String value, TigerConfigurationLoader source) {
        String result = value;
        result = replacePlaceholderWithGivenIntro(result, '$', source::readStringOptional);
        result = replacePlaceholderWithGivenIntro(result, '!', TigerJexlExecutor::executeOptional);
        result = replacePlaceholderWithGivenIntro(result, '$', source::readStringOptional);
        return result;
    }

    private static String replacePlaceholderWithGivenIntro(String str, char intro,
        Function<String, Optional<String>> placeholderResolver) {
        final String tokenStr = intro + "{";
        int varIdx = str.indexOf(tokenStr);
        while (varIdx != -1) {
            final int endVar = str.indexOf('}', varIdx);
            if (endVar == -1) {
                return str;
            }
            final String placeholderString = str.substring(varIdx + tokenStr.length(), endVar);
            final Optional<String> valueOptional = placeholderResolver.apply(placeholderString);
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
