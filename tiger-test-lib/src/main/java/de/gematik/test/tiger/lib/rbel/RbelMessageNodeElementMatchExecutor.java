/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package de.gematik.test.tiger.lib.rbel;

import static de.gematik.test.tiger.lib.rbel.RbelMessageValidator.getValueOrContentString;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.RbelElement;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

/**
 * Executor that takes a list of elements and performs matching / isEqual checks. If any elements
 * match for shouldMatch == TRUE or if none match for shouldMatch == FALSE this executor succeeds.
 * Else it will throw an AssertionError.
 */
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
@Slf4j
public class RbelMessageNodeElementMatchExecutor {

  private final String rbelPath;
  private final String oracle;
  private final boolean shouldMatch;
  private final List<RbelElement> elements;

  @Nullable private Pattern regexPattern;
  private boolean foundMatchingNode;

  public void execute() {
    assertThat(oracle)
        .withFailMessage("Matching string must not be empty for rbel path {}", rbelPath)
        .isNotBlank();

    compileRegexPattern();
    for (RbelElement element : elements) {
      if (foundMatchingNode) break;
      doesThisElementMatch(element);
    }
    if (shouldMatch && !foundMatchingNode) {
      if (elements.size() == 1) {
        throw new AssertionError(
            "Element value:\n" + elements.get(0).getRawStringContent() + "\nExpected:\n" + oracle);
      } else {
        throw new AssertionError(
            String.format(
                "Expected that nodes to rbel path '%s' are equal to or match '%s'",
                rbelPath, oracle));
      }
    }
  }

  private void compileRegexPattern() {
    try {
      regexPattern = Pattern.compile(oracle, Pattern.MULTILINE | Pattern.DOTALL);
    } catch (PatternSyntaxException pse) {
      log.atTrace()
          .addArgument(oracle)
          .addArgument(pse::getMessage)
          .log("Ignoring oracle '{}' as regex-pattern, error while compiling: '{}'");
      // nothing to do as we ignore invalid patterns (could be meant for isEqual checks)
    }
  }

  private void doesThisElementMatch(RbelElement element) {
    String text = getValueOrContentString(element);
    boolean itMatches =
        text.equals(oracle) || (regexPattern != null && regexPattern.matcher(text).matches());
    if (shouldMatch) {
      if (itMatches) {
        foundMatchingNode = true;
      }
    } else {
      if (itMatches) {
        throw new AssertionError(
            String.format(
                "Did not expect that value '%s' of node '%s' is equal to or matches '%s'",
                text, rbelPath, oracle));
      }
    }
  }
}
