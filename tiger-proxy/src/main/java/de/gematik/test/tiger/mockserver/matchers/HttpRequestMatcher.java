package de.gematik.test.tiger.mockserver.matchers;

import de.gematik.test.tiger.mockserver.mock.Expectation;
import de.gematik.test.tiger.mockserver.mock.listeners.MockServerMatcherNotifier;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.RequestDefinition;
import java.util.List;

public interface HttpRequestMatcher extends Matcher<RequestDefinition> {

  List<HttpRequest> getHttpRequests();

  boolean matches(final RequestDefinition request);

  boolean matches(MatchDifference context, RequestDefinition httpRequest);

  Expectation getExpectation();

  boolean update(Expectation expectation);

  boolean update(RequestDefinition requestDefinition);

  @SuppressWarnings("UnusedReturnValue")
  HttpRequestMatcher setResponseInProgress(boolean responseInProgress);

  boolean isResponseInProgress();

  MockServerMatcherNotifier.Cause getSource();

  @SuppressWarnings("UnusedReturnValue")
  HttpRequestMatcher withSource(MockServerMatcherNotifier.Cause source);

  boolean isActive();
}
