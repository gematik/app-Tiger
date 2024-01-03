/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common;

import de.gematik.test.tiger.common.config.TigerConfigurationLoader;
import de.gematik.test.tiger.common.exceptions.TigerJexlException;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BiFunction;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.lang3.tuple.Pair;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TokenSubstituteHelper {

  private static final Deque<Pair<Character, ReplacerFunction>> REPLACER_ORDER =
      new ConcurrentLinkedDeque<>();
  private static final int MAXIMUM_NUMBER_OF_REPLACEMENTS = 1_000;

  @Getter @Setter
  private static BiFunction<String, TigerConfigurationLoader, Optional<String>> resolve =
      (key, config) -> config.readStringOptional(key);

  static {
    REPLACER_ORDER.add(Pair.of('$', TokenSubstituteHelper::replaceWithConfigurationValue));
    REPLACER_ORDER.add(
        Pair.of(
            '!',
            (str, source, ctx) -> {
              try {
                return TigerJexlExecutor.evaluateJexlExpression(
                        str, ctx.orElseGet(TigerJexlContext::new))
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

  private static Optional<String> replaceWithConfigurationValue(
      String str, TigerConfigurationLoader source, Optional<TigerJexlContext> ctx) {
    if (ctx.isPresent() && ctx.get().has(str)) {
      return ctx.map(c -> c.get(str).toString());
    }
    if (str.contains("{") || str.contains("}")) {
      return Optional.empty();
    }
    Optional<String> fallbackValue =
        Optional.of(str)
            .filter(s -> s.contains("|"))
            .map(
                s ->
                    Optional.of(s.split("\\|", 2))
                        .filter(split -> split.length == 2)
                        .map(split -> split[1])
                        .orElse(""));
    Optional<String> key = Optional.of(str).map(s -> s.split("\\|")[0]);
    return key.flatMap(k -> TokenSubstituteHelper.resolve.apply(k, source))
        .or(() -> key.flatMap(k -> ctx.map(context -> context.get(k)).map(Object::toString)))
        .or(() -> ctx.map(context -> context.get(str)).map(Object::toString))
        .or(() -> fallbackValue);
  }

  public static String substitute(final String value, TigerConfigurationLoader source) {
    return substitute(value, source, Optional.empty());
  }

  public static String substitute(
      final String value,
      TigerConfigurationLoader source,
      @NonNull Optional<TigerJexlContext> context) {
    String result = value;
    boolean keepOnReplacing = true;
    int iterationsLeft = MAXIMUM_NUMBER_OF_REPLACEMENTS;
    while (keepOnReplacing) {
      keepOnReplacing = false;
      for (Pair<Character, ReplacerFunction> replacer : REPLACER_ORDER) {
        final Optional<String> replacedOptional =
            replacePlaceholderWithGivenIntro(
                result, replacer.getKey(), replacer.getValue(), source, context);
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

  private static Optional<String> replacePlaceholderWithGivenIntro(
      final String str,
      char intro,
      ReplacerFunction placeholderResolver,
      TigerConfigurationLoader source,
      Optional<TigerJexlContext> context) {
    final String tokenStr = intro + "{";
    int varIdx = str.indexOf(tokenStr);
    while (varIdx != -1) {
      final int endVar = str.indexOf('}', varIdx);
      if (endVar == -1) {
        return Optional.empty();
      }
      final String placeholderString = str.substring(varIdx + tokenStr.length(), endVar);
      final Optional<String> valueOptional =
          placeholderResolver.replace(placeholderString, source, context);
      if (valueOptional.isPresent()) {
        return Optional.of(
            str.substring(0, varIdx) + valueOptional.get() + str.substring(endVar + 1));
      } else {
        varIdx = str.indexOf(tokenStr, varIdx + 1);
      }
    }
    return Optional.empty();
  }

  public static Deque<Pair<Character, ReplacerFunction>> getReplacerOrder() {
    return REPLACER_ORDER;
  }

  public interface ReplacerFunction {

    Optional<String> replace(
        String input, TigerConfigurationLoader source, Optional<TigerJexlContext> context);
  }
}
