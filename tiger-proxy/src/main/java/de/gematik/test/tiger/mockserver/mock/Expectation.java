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

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.gematik.test.tiger.mockserver.matchers.TimeToLive;
import de.gematik.test.tiger.mockserver.matchers.Times;
import de.gematik.test.tiger.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import de.gematik.test.tiger.mockserver.model.*;
import de.gematik.test.tiger.mockserver.uuid.UUIDService;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/*
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
@EqualsAndHashCode(exclude = {"id", "created", "sortableExpectationId"})
@Accessors(chain = true)
@Getter
public class Expectation extends ObjectWithJsonToString {

  private static final AtomicInteger EXPECTATION_COUNTER = new AtomicInteger(0);
  private static final long START_TIME = System.currentTimeMillis();
  private String id;
  @JsonIgnore private long created;
  private int priority;
  private SortableExpectationId sortableExpectationId;
  private final RequestDefinition httpRequest;
  private final Times times;
  private final TimeToLive timeToLive;
  @Setter private HttpAction httpAction;

  /**
   * Specify the HttpRequest to match against as follows:
   *
   * <p>
   *
   * <pre>
   *     when(
   *         request()
   *             .withMethod("GET")
   *             .withPath("/some/path")
   *     ).thenRespond(
   *         response()
   *             .withContentType(APPLICATION_JSON_UTF_8)
   *             .withBody("{\"some\": \"body\"}")
   *     );
   * </pre>
   *
   * <p>
   *
   * @param httpRequest the HttpRequest to match against
   * @return the Expectation
   */
  public static Expectation when(HttpRequest httpRequest) {
    return new Expectation(httpRequest);
  }

  /**
   * Specify the HttpRequest to match against with a match priority as follows:
   *
   * <p>
   *
   * <pre>
   *     when(
   *         request()
   *             .withMethod("GET")
   *             .withPath("/some/path"),
   *         10
   *     ).thenRespond(
   *         response()
   *             .withContentType(APPLICATION_JSON_UTF_8)
   *             .withBody("{\"some\": \"body\"}")
   *     );
   * </pre>
   *
   * <p>
   *
   * @param httpRequest the HttpRequest to match against
   * @param priority the priority with which this expectation is used to match requests compared to
   *     other expectations (high first)
   * @return the Expectation
   */
  public static Expectation when(HttpRequest httpRequest, int priority) {
    return new Expectation(httpRequest, Times.unlimited(), TimeToLive.unlimited(), priority);
  }

  /**
   * Specify the HttpRequest to match against for a limit number of times or time as follows:
   *
   * <p>
   *
   * <pre>
   *     when(
   *         request()
   *             .withMethod("GET")
   *             .withPath("/some/path"),
   *         5,
   *         exactly(TimeUnit.SECONDS, 90)
   *     ).thenRespond(
   *         response()
   *             .withContentType(APPLICATION_JSON_UTF_8)
   *             .withBody("{\"some\": \"body\"}")
   *     );
   * </pre>
   *
   * <p>
   *
   * @param httpRequest the HttpRequest to match against
   * @param times the number of times to use this expectation to match requests
   * @param timeToLive the time this expectation should be used to match requests
   * @return the Expectation
   */
  public static Expectation when(HttpRequest httpRequest, Times times, TimeToLive timeToLive) {
    return new Expectation(httpRequest, times, timeToLive, 0);
  }

  /**
   * Specify the HttpRequest to match against for a limit number of times or time and a match
   * priority as follows:
   *
   * <p>
   *
   * <pre>
   *     when(
   *         request()
   *             .withMethod("GET")
   *             .withPath("/some/path"),
   *         5,
   *         exactly(TimeUnit.SECONDS, 90),
   *         10
   *     ).thenRespond(
   *         response()
   *             .withContentType(APPLICATION_JSON_UTF_8)
   *             .withBody("{\"some\": \"body\"}")
   *     );
   * </pre>
   *
   * <p>
   *
   * @param httpRequest the HttpRequest to match against
   * @param times the number of times to use this expectation to match requests
   * @param timeToLive the time this expectation should be used to match requests
   * @param priority the priority with which this expectation is used to match requests compared to
   *     other expectations (high first)
   * @return the Expectation
   */
  public static Expectation when(
      HttpRequest httpRequest, Times times, TimeToLive timeToLive, int priority) {
    return new Expectation(httpRequest, times, timeToLive, priority);
  }

  public Expectation(RequestDefinition requestDefinition) {
    this(requestDefinition, Times.unlimited(), TimeToLive.unlimited(), 0);
  }

  public Expectation(
      RequestDefinition requestDefinition, Times times, TimeToLive timeToLive, int priority) {
    // ensure created enforces insertion order by relying on system time, and a counter
    EXPECTATION_COUNTER.compareAndSet(Integer.MAX_VALUE, 0);
    this.created = System.currentTimeMillis() - START_TIME + EXPECTATION_COUNTER.incrementAndGet();
    this.httpRequest = requestDefinition;
    this.times = times;
    this.timeToLive = timeToLive;
    this.priority = priority;
  }

  /**
   * Set id of this expectation which can be used to update this expectation later or for clearing
   * or verifying by expectation id.
   *
   * <p>Note: Each unique expectation must have a unique id otherwise this expectation will update a
   * existing expectation with the same id.
   *
   * @param id unique string for expectation's id
   */
  public Expectation withId(String id) {
    this.id = id;
    this.sortableExpectationId = null;
    return this;
  }

  public String getId() {
    if (id == null) {
      withId(UUIDService.getUUID());
    }
    return id;
  }

  public Expectation withCreated(long created) {
    this.created = created;
    this.sortableExpectationId = null;
    return this;
  }

  @JsonIgnore
  public SortableExpectationId getSortableId() {
    if (sortableExpectationId == null) {
      sortableExpectationId = new SortableExpectationId(getId(), priority, created);
    }
    return sortableExpectationId;
  }

  public Expectation thenRespond(HttpResponse httpResponse) {
    this.httpAction = HttpAction.of(httpResponse);
    return this;
  }

  public Expectation thenForward(ExpectationForwardAndResponseCallback callback) {
    this.httpAction =
        HttpAction.of(new HttpOverrideForwardedRequest())
            .setExpectationForwardAndResponseCallback(callback);
    return this;
  }

  @JsonIgnore
  public boolean isActive() {
    return hasRemainingMatches() && isStillAlive();
  }

  private boolean hasRemainingMatches() {
    return times == null || times.greaterThenZero();
  }

  private boolean isStillAlive() {
    return timeToLive == null || timeToLive.stillAlive();
  }

  public boolean decrementRemainingMatches() {
    if (times != null) {
      return times.decrement();
    }
    return false;
  }

  @SuppressWarnings("PointlessNullCheck")
  public boolean contains(HttpRequest httpRequest) {
    return httpRequest != null && this.httpRequest.equals(httpRequest);
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  public Expectation clone() {
    return new Expectation(httpRequest, times.clone(), timeToLive, priority)
        .withId(id)
        .withCreated(created)
        .setHttpAction(httpAction);
  }
}
