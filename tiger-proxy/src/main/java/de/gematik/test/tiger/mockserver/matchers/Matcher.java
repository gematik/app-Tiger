package de.gematik.test.tiger.mockserver.matchers;

public interface Matcher<T> {

  boolean matches(MatchDifference context, T t);

  boolean isBlank();
}
