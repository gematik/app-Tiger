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
import de.gematik.test.tiger.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Slf4j
@RequiredArgsConstructor
public class BooleanMatcher extends ObjectWithReflectiveEqualsHashCodeToString
    implements Matcher<Boolean> {
  private static final String[] excludedFields = {"mockServerLogger"};
  private final Boolean matcher;

  @Override
  public boolean matches(final MatchDifference context, Boolean matched) {
    boolean result = false;

    if (matcher == null) {
      result = true;
    } else if (matched != null) {
      result = matched.equals(matcher);
    }

    if (!result && context != null) {
      context.addDifference("boolean match failed expected:{}found:{}", this.matcher, matched);
    }

    return result;
  }

  public boolean isBlank() {
    return matcher == null;
  }

  @Override
  @JsonIgnore
  public String[] fieldsExcludedFromEqualsAndHashCode() {
    return excludedFields;
  }
}
