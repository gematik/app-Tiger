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

package de.gematik.test.tiger.mockserver.mock;

import static de.gematik.test.tiger.mockserver.mock.SortableExpectationId.EXPECTATION_SORTABLE_PRIORITY_COMPARATOR;
import static de.gematik.test.tiger.mockserver.mock.SortableExpectationId.NULL;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import de.gematik.test.tiger.mockserver.collections.CircularHashMap;
import de.gematik.test.tiger.mockserver.collections.CircularPriorityQueue;
import de.gematik.test.tiger.mockserver.configuration.Configuration;
import de.gematik.test.tiger.mockserver.matchers.HttpRequestMatcher;
import de.gematik.test.tiger.mockserver.matchers.MatchDifference;
import de.gematik.test.tiger.mockserver.matchers.MatcherBuilder;
import de.gematik.test.tiger.mockserver.mock.listeners.MockServerMatcherNotifier;
import de.gematik.test.tiger.mockserver.model.*;
import de.gematik.test.tiger.mockserver.scheduler.Scheduler;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@SuppressWarnings("FieldMayBeFinal")
@Slf4j
public class RequestMatchers extends MockServerMatcherNotifier {

  private static final int MAX_EXPECTATIONS = 1000;
  final CircularPriorityQueue<String, HttpRequestMatcher, SortableExpectationId>
      httpRequestMatchers;
  final CircularHashMap<String, RequestDefinition> expectationRequestDefinitions;
  private final Configuration configuration;
  private final Scheduler scheduler;
  private MatcherBuilder matcherBuilder;

  public RequestMatchers(Configuration configuration, Scheduler scheduler) {
    super(scheduler);
    this.configuration = configuration;
    this.scheduler = scheduler;
    this.matcherBuilder = new MatcherBuilder(configuration);
    httpRequestMatchers =
        new CircularPriorityQueue<>(
            MAX_EXPECTATIONS,
            EXPECTATION_SORTABLE_PRIORITY_COMPARATOR,
            httpRequestMatcher ->
                httpRequestMatcher.getExpectation() != null
                    ? httpRequestMatcher.getExpectation().getSortableId()
                    : NULL,
            httpRequestMatcher ->
                httpRequestMatcher.getExpectation() != null
                    ? httpRequestMatcher.getExpectation().getId()
                    : "");
    expectationRequestDefinitions = new CircularHashMap<>(MAX_EXPECTATIONS);
    log.trace("expectation circular priority queue created");
  }

  public Expectation add(Expectation expectation, Cause cause) {
    Expectation upsertedExpectation = null;
    if (expectation != null) {
      expectationRequestDefinitions.put(expectation.getId(), expectation.getHttpRequest());
      upsertedExpectation =
          httpRequestMatchers
              .getByKey(expectation.getId())
              .map(
                  httpRequestMatcher -> {
                    if (httpRequestMatcher.getExpectation() != null) {
                      // propagate created time from previous entry to avoid re-ordering on update
                      expectation.withCreated(httpRequestMatcher.getExpectation().getCreated());
                    }
                    httpRequestMatchers.removePriorityKey(httpRequestMatcher);
                    if (httpRequestMatcher.update(expectation)) {
                      httpRequestMatchers.addPriorityKey(httpRequestMatcher);
                      log.info(
                          "updated expectation:{} with id:{}",
                          expectation.clone(),
                          expectation.getId());
                    } else {
                      httpRequestMatchers.addPriorityKey(httpRequestMatcher);
                    }
                    return httpRequestMatcher;
                  })
              .orElseGet(() -> addPrioritisedExpectation(expectation, cause))
              .getExpectation();
      notifyListeners(this, cause);
    }
    return upsertedExpectation;
  }

  private HttpRequestMatcher addPrioritisedExpectation(Expectation expectation, Cause cause) {
    HttpRequestMatcher httpRequestMatcher = matcherBuilder.transformsToMatcher(expectation);
    httpRequestMatchers.add(httpRequestMatcher);
    httpRequestMatcher.withSource(cause);
    log.trace(
        "creating expectation: {} with id:{}", expectation.getHttpRequest(), expectation.getId());
    return httpRequestMatcher;
  }

  public int size() {
    return httpRequestMatchers.size();
  }

  public void reset(Cause cause) {
    httpRequestMatchers.stream()
        .forEach(httpRequestMatcher -> removeHttpRequestMatcher(httpRequestMatcher, cause, false));
    expectationRequestDefinitions.clear();
    notifyListeners(this, cause);
  }

  public void reset() {
    reset(Cause.API);
  }

  public Expectation firstMatchingExpectation(HttpRequest httpRequest) {
    Optional<Expectation> first =
        getHttpRequestMatchersCopy()
            .map(
                httpRequestMatcher -> {
                  Expectation matchingExpectation = null;
                  boolean remainingMatchesDecremented = false;
                  if (httpRequestMatcher.matches(
                      new MatchDifference(configuration.detailedMatchFailures(), httpRequest),
                      httpRequest)) {
                    matchingExpectation = httpRequestMatcher.getExpectation();
                    httpRequestMatcher.setResponseInProgress(true);
                    if (matchingExpectation.decrementRemainingMatches()) {
                      remainingMatchesDecremented = true;
                    }
                  } else if (!httpRequestMatcher.isResponseInProgress()
                      && !httpRequestMatcher.isActive()) {
                    scheduler.submit(() -> removeHttpRequestMatcher(httpRequestMatcher));
                  }
                  if (remainingMatchesDecremented) {
                    notifyListeners(this, Cause.API);
                  }
                  return matchingExpectation;
                })
            .filter(Objects::nonNull)
            .findFirst();
    return first.orElse(null);
  }

  public void clear(RequestDefinition requestDefinition) {
    if (requestDefinition != null) {
      HttpRequestMatcher clearHttpRequestMatcher =
          matcherBuilder.transformsToMatcher(requestDefinition);
      getHttpRequestMatchersCopy()
          .forEach(
              httpRequestMatcher -> {
                RequestDefinition request = httpRequestMatcher.getExpectation().getHttpRequest();
                if (isNotBlank(requestDefinition.getLogCorrelationId())) {
                  request =
                      request
                          .shallowClone()
                          .withLogCorrelationId(requestDefinition.getLogCorrelationId());
                }
                if (clearHttpRequestMatcher.matches(request)) {
                  removeHttpRequestMatcher(httpRequestMatcher);
                }
              });
      log.info("cleared expectations that match: {}", requestDefinition);
    } else {
      reset();
    }
  }

  public void clear(ExpectationId expectationId) {
    if (expectationId != null) {
      httpRequestMatchers.getByKey(expectationId.id()).ifPresent(this::removeHttpRequestMatcher);
      log.info("cleared expectations that have id:{}", expectationId.id());
    } else {
      reset();
    }
  }

  private void removeHttpRequestMatcher(HttpRequestMatcher httpRequestMatcher) {
    removeHttpRequestMatcher(httpRequestMatcher, Cause.API, true);
  }

  @SuppressWarnings("rawtypes")
  private void removeHttpRequestMatcher(
      HttpRequestMatcher httpRequestMatcher, Cause cause, boolean notifyAndUpdateMetrics) {
    if (httpRequestMatchers.remove(httpRequestMatcher)) {
      if (httpRequestMatcher.getExpectation() != null && log.isInfoEnabled()) {
        Expectation expectation = httpRequestMatcher.getExpectation().clone();
        log.info("removed expectation:{} with id:{}", expectation, expectation.getId());
      }
      if (notifyAndUpdateMetrics) {
        notifyListeners(this, cause);
      }
    }
  }

  public List<Expectation> retrieveActiveExpectations(RequestDefinition requestDefinition) {
    if (requestDefinition == null) {
      return httpRequestMatchers.stream()
          .map(HttpRequestMatcher::getExpectation)
          .collect(Collectors.toList());
    } else {
      List<Expectation> expectations = new ArrayList<>();
      HttpRequestMatcher requestMatcher = matcherBuilder.transformsToMatcher(requestDefinition);
      getHttpRequestMatchersCopy()
          .forEach(
              httpRequestMatcher -> {
                if (requestMatcher.matches(httpRequestMatcher.getExpectation().getHttpRequest())) {
                  expectations.add(httpRequestMatcher.getExpectation());
                }
              });
      return expectations;
    }
  }

  public boolean isEmpty() {
    return httpRequestMatchers.isEmpty();
  }

  protected void notifyListeners(final RequestMatchers notifier, Cause cause) {
    super.notifyListeners(notifier, cause);
  }

  private Stream<HttpRequestMatcher> getHttpRequestMatchersCopy() {
    return httpRequestMatchers.stream();
  }
}
