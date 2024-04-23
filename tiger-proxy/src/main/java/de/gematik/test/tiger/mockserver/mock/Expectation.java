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

import de.gematik.test.tiger.mockserver.mock.action.ExpectationCallback;
import de.gematik.test.tiger.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import de.gematik.test.tiger.mockserver.model.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.PatternSyntaxException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;

/*
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
@EqualsAndHashCode(
    exclude = {"created", "sortableExpectationId"},
    callSuper = false)
@Accessors(chain = true)
@Getter
@Slf4j
public class Expectation extends ObjectWithJsonToString implements Comparable<Expectation> {

  private static final AtomicInteger EXPECTATION_COUNTER = new AtomicInteger(0);
  private static final long START_TIME = System.currentTimeMillis();
  @Setter private String id = UUID.randomUUID().toString();
  private final int priority;
  private final HttpRequest requestPattern;
  @Setter private HttpAction httpAction;
  private ExpectationCallback expectationCallback;

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
    if (o == null) {
      return 1;
    }
    if (priority == o.priority) {
      if (requestPattern == null
          || o.requestPattern == null
          || requestPattern.getPath() == null
          || o.requestPattern.getPath() == null) {
        return 0;
      }
      final String thisPath = requestPattern.getPath();
      final String otherPath = o.requestPattern.getPath();
      if (uriTwoIsBelowUriOne(thisPath, otherPath)) {
        return -1;
      } else if (uriTwoIsBelowUriOne(otherPath, thisPath)) {
        return 1;
      } else {
        return 0;
      }
    }
    return Integer.compare(o.priority, priority);
  }

  private static boolean uriTwoIsBelowUriOne(final String value1, final String value2) {
    try {
      final URI uri1 = new URI(value1);
      final URI uri2WithUri1Scheme = new URIBuilder(value2).setScheme(uri1.getScheme()).build();
      return !uri1.relativize(uri2WithUri1Scheme).equals(uri2WithUri1Scheme);
    } catch (final URISyntaxException e) {
      return false;
    }
  }
}
