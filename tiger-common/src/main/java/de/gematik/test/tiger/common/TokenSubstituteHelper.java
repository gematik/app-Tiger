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
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TokenSubstituteHelper {

    public static final Deque<Pair<Character, ReplacerFunction>> REPLACER_ORDER = new ConcurrentLinkedDeque<>();
    private static final int MAXIMUM_NUMBER_OF_REPLACEMENTS = 1_000;

    static {
        REPLACER_ORDER.add(Pair.of('$', (str, source, ctx) -> source.readStringOptional(str)));
        REPLACER_ORDER.add(Pair.of('!', (str, source, ctx) -> TigerJexlExecutor.evaluateJexlExpression(str, ctx.orElseGet(TigerJexlContext::new))
            .map(Object::toString)));
    }

    public static String substitute(String value, TigerConfigurationLoader source) {
        return substitute(value, source, Optional.empty());
    }

    public static String substitute(String value, TigerConfigurationLoader source, Optional<TigerJexlContext> context) {
        String result = value;
        boolean keepOnReplacing = true;
        int iterationsLeft = MAXIMUM_NUMBER_OF_REPLACEMENTS;
        while (keepOnReplacing) {
            keepOnReplacing = false;
            for (Pair<Character, ReplacerFunction> replacer : REPLACER_ORDER) {
                final Optional<String> replacedOptional = replacePlaceholderWithGivenIntro(result,
                    replacer.getKey(),
                    replacer.getValue(),
                    source,
                    context);
                if (replacedOptional.isPresent()) {
                    result = replacedOptional.get();
                    keepOnReplacing = true;
                }
            }

            if (iterationsLeft-- <= 0) {
                break;
            }
        }
        return result;
    }

    private static Optional<String> replacePlaceholderWithGivenIntro(final String str, char intro,
        ReplacerFunction placeholderResolver, TigerConfigurationLoader source, Optional<TigerJexlContext> context) {
        final String tokenStr = intro + "{";
        int varIdx = str.indexOf(tokenStr);
        while (varIdx != -1) {
            final int endVar = str.indexOf('}', varIdx);
            if (endVar == -1) {
                return Optional.empty();
            }
            final String placeholderString = str.substring(varIdx + tokenStr.length(), endVar);
            final Optional<String> valueOptional = placeholderResolver.replace(placeholderString, source, context);
            if (valueOptional.isPresent()) {
                return Optional.of(str.substring(0, varIdx) + valueOptional.get() + str.substring(endVar + 1));
            } else {
                varIdx = str.indexOf(tokenStr, varIdx + 1);
            }
        }
        return Optional.empty();
    }

    public interface ReplacerFunction {

        Optional<String> replace(String input, TigerConfigurationLoader source, Optional<TigerJexlContext> context);
    }
}
