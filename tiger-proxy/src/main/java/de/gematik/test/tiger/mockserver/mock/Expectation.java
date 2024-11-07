/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.mockserver.mock;

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerRoute;
import de.gematik.test.tiger.mockserver.mock.action.ExpectationCallback;
import de.gematik.test.tiger.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import de.gematik.test.tiger.mockserver.model.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.PatternSyntaxException;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;

/*
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Getter
@Slf4j
@Builder(toBuilder = true)
@AllArgsConstructor
public class Expectation extends ObjectWithJsonToString implements Comparable<Expectation> {

  @Setter private String id = UUID.randomUUID().toString();
  @Setter private TigerRoute tigerRoute;
  private final int priority;
  private final HttpRequest requestPattern;
  @Setter private HttpAction httpAction;
  private final List<String> hostRegexes;
  private final boolean ignorePortsInHostHeader;
  private ExpectationCallback expectationCallback;

  public Expectation(HttpRequest requestDefinition, int priority, List<String> hostRegexes) {
    this.requestPattern = requestDefinition;
    this.priority = priority;
    if (hostRegexes == null) {
      this.hostRegexes = List.of();
      this.ignorePortsInHostHeader = true;
    } else {
      this.hostRegexes = hostRegexes.stream().map(String::trim).toList();
      this.ignorePortsInHostHeader = hostRegexes.stream().noneMatch(h -> h.contains(":"));
    }
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
        && secureMatches(request)
        && hostMatches(request)
        && pathMatches(this.requestPattern.getPath(), request.getPath())
        && (expectationCallback == null || expectationCallback.matches(request));
  }

  private boolean secureMatches(HttpRequest request) {
    if (requestPattern.isSecure() == null || request.isSecure() == null) {
      return true;
    }
    final boolean equals = Objects.equals(requestPattern.isSecure(), request.isSecure());
    if (!equals) {
      log.atTrace()
          .addArgument(request::isSecure)
          .addArgument(
              () -> {
                if (tigerRoute == null) {
                  return null;
                } else {
                  return tigerRoute.createShortDescription();
                }
              })
          .log("secure [{}] is not matching for route {}");
    }
    return equals;
  }

  private boolean hostMatches(HttpRequest request) {
    if (!requestPattern.getHeaders().containsEntry("Host")
        && (hostRegexes == null || hostRegexes.isEmpty())) {
      return true;
    }
    final String cleanedHostHeader =
        ignorePortsInHostHeader
            ? request.getFirstHeader("Host").trim().split(":")[0]
            : request.getFirstHeader("Host").trim();
    final String cleanedPatternHostHeader =
        ignorePortsInHostHeader
            ? requestPattern.getFirstHeader("Host").split(":")[0]
            : requestPattern.getFirstHeader("Host");
    if (StringUtils.equals(cleanedPatternHostHeader, cleanedHostHeader)) {
      return true;
    }
    final boolean anyHostHeaderMatch =
        hostRegexes.stream()
            .anyMatch(
                hostMatchCriterion ->
                    cleanedHostHeader.equals(hostMatchCriterion)
                        || cleanedHostHeader.matches(hostMatchCriterion));
    if (!anyHostHeaderMatch) {
      log.atTrace()
          .addArgument(() -> cleanedHostHeader)
          .addArgument(tigerRoute::createShortDescription)
          .addArgument(ignorePortsInHostHeader)
          .log("host [{}] is not matching for route {} ({})");
    }
    return anyHostHeaderMatch;
  }

  private boolean protocolMatches(HttpProtocol protocol, HttpProtocol otherProtocol) {
    if (protocol == null) {
      return true;
    } else {
      if (protocol.equals(otherProtocol)) {
        return true;
      } else {
        log.atTrace()
            .addArgument(protocol)
            .addArgument(otherProtocol)
            .addArgument(tigerRoute::createShortDescription)
            .log("protocol [{}] is not matching [{}] for route {}");
        return false;
      }
    }
  }

  public boolean pathMatches(String blueprint, String actualValue) {
    if (blueprint == null) {
      return true;
    }
    if (StringUtils.isBlank(blueprint)) {
      return true;
    } else {
      if (actualValue != null) {
        // match as exact string
        if (actualValue.equalsIgnoreCase(blueprint)) {
          return true;
        }

        // match as regex - matcher -> matched (data plane or control plane)
        try {
          if (actualValue.matches(blueprint)) {
            return true;
          }
        } catch (PatternSyntaxException pse) {
          log.debug("error while matching regex [{}] for string [{}]", blueprint, actualValue, pse);
        }
        // match as regex - matched -> matcher (control plane only)
        try {
          if (blueprint.matches(actualValue)) {
            return true;
          }
        } catch (PatternSyntaxException pse) {
          log.debug("error while matching regex [{}] for string [{}]", actualValue, blueprint, pse);
        }
      }
    }
    log.atTrace()
        .addArgument(actualValue)
        .addArgument(tigerRoute::createShortDescription)
        .log("path [{}] is not matching for route {}");
    return false;
  }

  @Override
  public int compareTo(Expectation o) {
    if (o == null) {
      return 1;
    }
    if (priority != o.priority) {
      return Integer.compare(o.priority, priority);
    }
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
      val otherHostSize = Optional.ofNullable(o.hostRegexes).map(List::size).orElse(0);
      val thisHostSize = Optional.ofNullable(this.hostRegexes).map(List::size).orElse(0);
      return Integer.compare(otherHostSize, thisHostSize);
    }
  }

  private static boolean uriTwoIsBelowUriOne(final String uri1, final String uri2) {
    try {
      URIBuilder base = new URIBuilder(uri2);
      URIBuilder sub = new URIBuilder(uri1);

      URI baseUri = base.build().normalize();
      URI subUri = sub.build().normalize();

      return subUri.getPath().startsWith(baseUri.getPath()) && !baseUri.equals(subUri);
    } catch (URISyntaxException e) {
      return false;
    }
  }

  public String createShortDescription() {
    if (tigerRoute != null) {
      return tigerRoute.createShortDescription();
    } else {
      return requestPattern.printLogLineDescription();
    }
  }
}
