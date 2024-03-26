/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.mock;

import de.gematik.test.tiger.mockserver.mock.action.ExpectationCallback;
import de.gematik.test.tiger.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import de.gematik.test.tiger.mockserver.model.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.PatternSyntaxException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/*
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
@EqualsAndHashCode(exclude = {"created", "sortableExpectationId"}, callSuper = false)
@Accessors(chain = true)
@Getter
@Slf4j
public class Expectation extends ObjectWithJsonToString implements Comparable<Expectation> {

  private static final AtomicInteger EXPECTATION_COUNTER = new AtomicInteger(0);
  private static final long START_TIME = System.currentTimeMillis();
  @Setter
  private String id = UUID.randomUUID().toString();
  private final int priority;
  private final HttpRequest requestPattern;
  @Setter private HttpAction httpAction;
  private ExpectationCallback expectationCallback;

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
    return new Expectation(httpRequest, priority);
  }

  public Expectation(HttpRequest requestDefinition) {
    this(requestDefinition, 0);
  }

  public Expectation(HttpRequest requestDefinition, int priority) {
    // ensure created enforces insertion order by relying on system time, and a counter
    EXPECTATION_COUNTER.compareAndSet(Integer.MAX_VALUE, 0);
    this.requestPattern = requestDefinition;
    this.priority = priority;
  }

  public Expectation thenForward(ExpectationForwardAndResponseCallback callback) {
    this.httpAction =
        HttpAction.of(new HttpOverrideForwardedRequest())
            .setExpectationForwardAndResponseCallback(callback);
    this.expectationCallback = callback;
    return this;
  }

  public boolean matches(HttpRequest request) {
    return protocolMatches(this.requestPattern.getProtocol(), request.getProtocol())
           && hostMatches(request)
           && pathMatches(this.requestPattern.getPath(), request.getPath())
           && (expectationCallback == null || expectationCallback.matches(request));
  }

  private boolean hostMatches(HttpRequest request) {
    if (!requestPattern.getHeaders().containsEntry("Host")) {
      return true;
    }
    return StringUtils.equals(
        requestPattern.getFirstHeader("Host"), request.getFirstHeader("Host"));
  }

  private boolean protocolMatches(Protocol protocol, Protocol otherProtocol) {
    if (protocol == null) {
      return true;
    } else {
      return protocol.equals(otherProtocol);
    }
  }

  public static boolean pathMatches(String matcherValue, String matchedValue) {
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
          log.debug(
              "error while matching regex [{}] for string [{}]", matcherValue, matchedValue, pse);
        }
        // match as regex - matched -> matcher (control plane only)
        try {
          if (matcherValue.matches(matchedValue)) {
            return true;
          }
        } catch (PatternSyntaxException pse) {
          log.trace(
              "error while matching regex [{}] for string [{}]", matchedValue, matcherValue, pse);
        }
      }
    }
    return false;
  }

  @Override
  public int compareTo(Expectation o) {
    return Integer.compare(o.priority, priority);
  }
}
