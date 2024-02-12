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

import de.gematik.test.tiger.mockserver.mock.Expectation;
import de.gematik.test.tiger.mockserver.mock.listeners.MockServerMatcherNotifier;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.RequestDefinition;
import java.util.List;

/*
 * @author jamesdbloom
 */
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
