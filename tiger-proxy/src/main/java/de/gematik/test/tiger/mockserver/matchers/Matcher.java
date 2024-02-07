/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.matchers;

/*
 * @author jamesdbloom
 */
public interface Matcher<T> {

  boolean matches(MatchDifference context, T t);

  boolean isBlank();
}
