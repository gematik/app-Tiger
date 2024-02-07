/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.matchers;

import de.gematik.test.tiger.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/*
 * @author jamesdbloom
 */
public abstract class NotMatcher<MatchedType> extends ObjectWithReflectiveEqualsHashCodeToString
    implements Matcher<MatchedType> {

  boolean not = false;

  public static <MatcherType extends NotMatcher<?>> MatcherType notMatcher(MatcherType matcher) {
    matcher.not = true;
    return matcher;
  }
}
