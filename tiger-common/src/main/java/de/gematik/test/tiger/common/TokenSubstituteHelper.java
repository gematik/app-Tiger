/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common;

import de.gematik.test.tiger.common.config.TigerConfigurationLoader;
import de.gematik.test.tiger.common.exceptions.TigerJexlException;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.lang3.tuple.Pair;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TokenSubstituteHelper {

    public static final Deque<Pair<Character, ReplacerFunction>> REPLACER_ORDER = new ConcurrentLinkedDeque<>();
    private static final int MAXIMUM_NUMBER_OF_REPLACEMENTS = 1_000;

    static {
        REPLACER_ORDER.add(Pair.of('$', (str, source, ctx) -> {
            if (ctx.isPresent() && ctx.get().has(str)) {
                return ctx.map(c -> c.get(str).toString());
            }
            return Optional.ofNullable(str)
                .filter(s -> !s.contains("{") && !s.contains("}"))
                .flatMap(source::readStringOptional)
                .or(() -> ctx.map(context -> context.get(str))
                    .filter(Objects::nonNull)
                    .map(Object::toString));
        }));
        REPLACER_ORDER.add(Pair.of('!', (str, source, ctx) -> {
            try {
                return TigerJexlExecutor.evaluateJexlExpression(str, ctx.orElseGet(TigerJexlContext::new))
                    .map(Object::toString);
            } catch (RuntimeException e) {
                if (e instanceof TigerJexlException
                    && e.getCause() instanceof JexlException
                    && e.getCause().getMessage().contains("parsing error in '{'")) {
                    return Optional.empty();
                }
                throw e;
            }
        }));
    }

    public static String substitute(String value, TigerConfigurationLoader source) {
        return substitute(value, source, Optional.empty());
    }

    public static String substitute(final String value, TigerConfigurationLoader source,
        @NonNull Optional<TigerJexlContext> context) {
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
