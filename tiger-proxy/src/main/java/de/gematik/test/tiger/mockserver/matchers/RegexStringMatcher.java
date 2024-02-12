/*
 * Copyright (c) 2024 gematik GmbH
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

package de.gematik.test.tiger.mockserver.matchers;


import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.regex.PatternSyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/*
 * @author jamesdbloom
 */
@Slf4j
public class RegexStringMatcher extends BodyMatcher<String> {

  private static final String[] EXCLUDED_FIELDS = {"mockServerLogger"};
  private final String matcher;
  private final boolean controlPlaneMatcher;

  public RegexStringMatcher(boolean controlPlaneMatcher) {
    this.controlPlaneMatcher = controlPlaneMatcher;
    this.matcher = null;
  }

  RegexStringMatcher(String matcher, boolean controlPlaneMatcher) {
    this.controlPlaneMatcher = controlPlaneMatcher;
    this.matcher = matcher;
  }

  public boolean matches(String matched) {
    return matches((MatchDifference) null, matched);
  }

  public boolean matches(final MatchDifference context, String matched) {
    boolean result = matcher == null || matches(context, matcher, matched);
    return not != result;
  }

  public boolean matches(String matcher, String matched) {
    return matches(null, matcher, matched);
  }

  public boolean matches(
      MatchDifference context,
      String matcherValue,
      String matchedValue) {
    if (matcherValue == null) {
      return true;
    }
    if (StringUtils.isBlank(matcherValue)) {
      return true;
    } else {
      if (matchedValue != null) {
        // match as exact string
        if (matchedValue.equals(matcherValue) || matchedValue.equalsIgnoreCase(matcherValue)) {
          return true;
        }

        // match as regex - matcher -> matched (data plane or control plane)
        try {
          if (matchedValue.matches(matcherValue)) {
            return true;
          }
        } catch (PatternSyntaxException pse) {
          log.debug("error while matching regex [{}] for string [{}]", matcher, matchedValue, pse);
        }
        // match as regex - matched -> matcher (control plane only)
        try {
          if (controlPlaneMatcher && matcherValue.matches(matchedValue)) {
            return true;
          } else if (matcherValue.matches(matchedValue)) {
            log.debug("matcher{}would match{}if matcher was used for control plane",matcher, matchedValue);
          }
        } catch (PatternSyntaxException pse) {
          if (controlPlaneMatcher) {
            log.debug("error while matching regex [{}] for string [{}]", matchedValue, matcher, pse);
          }
        }
      }
    }
    if (context != null) {
      context.addDifference(
          "string or regex match failed expected:{}found:{}",
          matcher,
          matchedValue);
    }

    return false;
  }

  public boolean isBlank() {
    return matcher == null || StringUtils.isBlank(matcher);
  }

  @Override
  @JsonIgnore
  protected String[] fieldsExcludedFromEqualsAndHashCode() {
    return EXCLUDED_FIELDS;
  }
}
