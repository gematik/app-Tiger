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

import static de.gematik.test.tiger.mockserver.character.Character.NEW_LINE;
import static de.gematik.test.tiger.mockserver.matchers.MatchDifference.Field.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Joiner;
import de.gematik.test.tiger.mockserver.codec.PathParametersDecoder;
import de.gematik.test.tiger.mockserver.configuration.Configuration;
import de.gematik.test.tiger.mockserver.model.*;
import de.gematik.test.tiger.mockserver.serialization.ObjectMapperFactory;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/*
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
@Slf4j
public class HttpRequestPropertiesMatcher extends AbstractHttpRequestMatcher {

  private static final String[] excludedFields = {
    "mockServerLogger",
    "methodMatcher",
    "pathMatcher",
    "pathParameterMatcher",
    "queryStringParameterMatcher",
    "bodyMatcher",
    "headerMatcher",
    "cookieMatcher",
    "keepAliveMatcher",
    "bodyDTOMatcher",
    "sslMatcher",
    "controlPlaneMatcher",
    "responseInProgress",
    "objectMapper"
  };
  private static final String COMMA = ",";
  private static final String REQUEST_NOT_OPERATOR_IS_ENABLED =
      COMMA + NEW_LINE + "request 'not' operator is enabled";
  private static final String EXPECTATION_REQUEST_NOT_OPERATOR_IS_ENABLED =
      COMMA + NEW_LINE + "expectation's request 'not' operator is enabled";
  private static final String EXPECTATION_REQUEST_MATCHER_NOT_OPERATOR_IS_ENABLED =
      COMMA + NEW_LINE + "expectation's request matcher 'not' operator is enabled";
  private static final PathParametersDecoder pathParametersParser = new PathParametersDecoder();
  private static final ObjectWriter TO_STRING_OBJECT_WRITER =
      ObjectMapperFactory.createObjectMapper(true, false);
  private int hashCode;
  private HttpRequest httpRequest;
  private List<HttpRequest> httpRequests;
  private RegexStringMatcher methodMatcher = null;
  private RegexStringMatcher pathMatcher = null;
  private MultiValueMapMatcher pathParameterMatcher = null;
  private MultiValueMapMatcher headerMatcher = null;
  private BooleanMatcher keepAliveMatcher = null;
  private BooleanMatcher sslMatcher = null;
  private ExactStringMatcher protocolMatcher = null;

  public HttpRequestPropertiesMatcher(Configuration configuration) {
    super(configuration);
  }

  public HttpRequest getHttpRequest() {
    return httpRequest;
  }

  @Override
  public List<HttpRequest> getHttpRequests() {
    return httpRequests;
  }

  @Override
  public boolean apply(RequestDefinition requestDefinition) {
    HttpRequest httpRequest =
        requestDefinition instanceof HttpRequest ? (HttpRequest) requestDefinition : null;
    if (this.httpRequest == null || !this.httpRequest.equals(httpRequest)) {
      this.hashCode = 0;
      this.httpRequest = httpRequest;
      this.httpRequests = Collections.singletonList(this.httpRequest);
      if (httpRequest != null) {
        withMethod(httpRequest.getMethod());
        withPath(httpRequest);
        withPathParameters(httpRequest.getPathParameters());
        withHeaders(httpRequest.getHeaders());
        withKeepAlive(httpRequest.getKeepAlive());
        withSsl(httpRequest.isSecure());
        withProtocol(httpRequest.getProtocol());
      }
      return true;
    } else {
      return false;
    }
  }

  private void withMethod(String method) {
    this.methodMatcher = new RegexStringMatcher(method, controlPlaneMatcher);
  }

  private void withPath(HttpRequest httpRequest) {
    this.pathMatcher =
        new RegexStringMatcher(
            pathParametersParser.normalisePathWithParametersForMatching(httpRequest),
            controlPlaneMatcher);
  }

  private void withPathParameters(Parameters parameters) {
    this.pathParameterMatcher =
        new MultiValueMapMatcher(parameters, controlPlaneMatcher);
  }

  private void withHeaders(Headers headers) {
    this.headerMatcher = new MultiValueMapMatcher(headers, controlPlaneMatcher);
  }

  private void withKeepAlive(Boolean keepAlive) {
    this.keepAliveMatcher = new BooleanMatcher(keepAlive);
  }

  private void withSsl(Boolean isSsl) {
    this.sslMatcher = new BooleanMatcher(isSsl);
  }

  private void withProtocol(Protocol protocol) {
    this.protocolMatcher =
        new ExactStringMatcher(protocol != null ? protocol.name() : null);
  }

  public boolean matches(final MatchDifference context, final RequestDefinition requestDefinition) {
    if (requestDefinition instanceof HttpRequest) {
      HttpRequest request = (HttpRequest) requestDefinition;
      StringBuilder becauseBuilder = new StringBuilder();
      boolean overallMatch = matches(context, request, becauseBuilder);
      if (!controlPlaneMatcher) {
        if (overallMatch) {
          log.trace(
              this.expectation == null ? REQUEST_DID_MATCH : EXPECTATION_DID_MATCH,
              request,
              (this.expectation == null ? this : this.expectation.clone()));
        }
      } else {
        becauseBuilder.replace(0, 1, "");
        String because = becauseBuilder.toString();
        log.trace(
            this.expectation == null
                ? didNotMatchRequestBecause
                : becauseBuilder.length() > 0
                    ? didNotMatchExpectationBecause
                    : didNotMatchExpectationWithoutBecause,
            request,
            (this.expectation == null ? this : this.expectation.clone()),
            because);
      }
      return overallMatch;
    } else {
      return requestDefinition == null;
    }
  }

  private boolean matches(
      MatchDifference context, HttpRequest request, StringBuilder becauseBuilder) {
    if (isActive()) {
      if (request == this.httpRequest) {
        return true;
      } else if (this.httpRequest == null) {
        return true;
      } else {
        MatchDifferenceCount matchDifferenceCount = new MatchDifferenceCount(request);
        if (request != null) {
          boolean methodMatches =
              StringUtils.isBlank(request.getMethod())
                  || matches(METHOD, context, methodMatcher, request.getMethod());
          if (failFast(
              methodMatcher,
              context,
              matchDifferenceCount,
              becauseBuilder,
              methodMatches,
              METHOD)) {
            return false;
          }

          boolean pathMatches =
              StringUtils.isBlank(request.getPath())
                  || matches(
                      PATH,
                      context,
                      pathMatcher,
                      controlPlaneMatcher
                          ? pathParametersParser.normalisePathWithParametersForMatching(request)
                          : request.getPath());
          Parameters pathParameters = null;
          try {
            pathParameters = pathParametersParser.extractPathParameters(httpRequest, request);
          } catch (IllegalArgumentException iae) {
            if (!httpRequest.getPath().isBlank()) {
              if (context != null) {
                context.currentField(PATH);
                context.addDifference(iae.getMessage());
              }
              pathMatches = false;
            }
          }
          if (failFast(
              pathMatcher, context, matchDifferenceCount, becauseBuilder, pathMatches, PATH)) {
            return false;
          }

          boolean headersMatch = matches(HEADERS, context, headerMatcher, request.getHeaders());
          if (failFast(
              headerMatcher,
              context,
              matchDifferenceCount,
              becauseBuilder,
              headersMatch,
              HEADERS)) {
            return false;
          }

          boolean pathParametersMatches = true;
          if (!httpRequest.getPath().isBlank()) {
            MultiValueMapMatcher pathParameterMatcher = this.pathParameterMatcher;
            if (controlPlaneMatcher) {
              Parameters controlPlaneParameters;
              try {
                controlPlaneParameters =
                    pathParametersParser.extractPathParameters(request, httpRequest);
              } catch (IllegalArgumentException iae) {
                controlPlaneParameters = new Parameters();
              }
              pathParameterMatcher =
                  new MultiValueMapMatcher(controlPlaneParameters, controlPlaneMatcher);
            }
            pathParametersMatches =
                matches(PATH_PARAMETERS, context, pathParameterMatcher, pathParameters);
          }
          if (failFast(
              this.pathParameterMatcher,
              context,
              matchDifferenceCount,
              becauseBuilder,
              pathParametersMatches,
              PATH_PARAMETERS)) {
            return false;
          }

          boolean keepAliveMatches =
              matches(KEEP_ALIVE, context, keepAliveMatcher, request.getKeepAlive());
          if (failFast(
              keepAliveMatcher,
              context,
              matchDifferenceCount,
              becauseBuilder,
              keepAliveMatches,
              KEEP_ALIVE)) {
            return false;
          }

          boolean sslMatches = matches(SECURE, context, sslMatcher, request.isSecure());
          if (failFast(
              sslMatcher, context, matchDifferenceCount, becauseBuilder, sslMatches, SECURE)) {
            return false;
          }

          boolean protocolMatches =
              matches(
                  PROTOCOL,
                  context,
                  protocolMatcher,
                  request.getProtocol() != null ? request.getProtocol().name() : null);
          if (failFast(
              protocolMatcher,
              context,
              matchDifferenceCount,
              becauseBuilder,
              protocolMatches,
              PROTOCOL)) {
            return false;
          }

          boolean combinedResultAreTrue =
              combinedResultAreTrue(
                  matchDifferenceCount.getFailures() == 0,
                  request.isNot(),
                  this.httpRequest.isNot(),
                  not);
          if (!controlPlaneMatcher && combinedResultAreTrue) {
            // ensure actions have path parameters available to them
            request.withPathParameters(pathParameters);
          }
          return combinedResultAreTrue;
        } else {
          return combinedResultAreTrue(true, this.httpRequest.isNot(), not);
        }
      }
    }
    return false;
  }

  private boolean failFast(
      Matcher<?> matcher,
      MatchDifference context,
      MatchDifferenceCount matchDifferenceCount,
      StringBuilder becauseBuilder,
      boolean fieldMatches,
      MatchDifference.Field fieldName) {
    // update because builder
    if (!controlPlaneMatcher) {
      becauseBuilder
          .append(NEW_LINE)
          .append(fieldName.getName())
          .append(fieldMatches ? MATCHED : DID_NOT_MATCH);
      if (context != null
          && context.getDifferences(fieldName) != null
          && !context.getDifferences(fieldName).isEmpty()) {
        becauseBuilder
            .append(COLON_NEW_LINES)
            .append(Joiner.on(NEW_LINE).join(context.getDifferences(fieldName)));
      }
    }
    if (!fieldMatches && !controlPlaneMatcher) {
        if (matchDifferenceCount.getHttpRequest().isNot()) {
          becauseBuilder.append(REQUEST_NOT_OPERATOR_IS_ENABLED);
        }
        if (this.httpRequest.isNot()) {
          becauseBuilder.append(EXPECTATION_REQUEST_NOT_OPERATOR_IS_ENABLED);
        }
        if (not) {
          becauseBuilder.append(EXPECTATION_REQUEST_MATCHER_NOT_OPERATOR_IS_ENABLED);
        }

    }
    // update match difference and potentially fail fast
    if (!fieldMatches) {
      matchDifferenceCount.incrementFailures();
    }
    if (matcher != null && !matcher.isBlank() && configuration.matchersFailFast()) {
      return combinedResultAreTrue(
          matchDifferenceCount.getFailures() != 0,
          matchDifferenceCount.getHttpRequest().isNot(),
          this.httpRequest.isNot(),
          not);
    }
    return false;
  }

  /** true for odd number of false inputs */
  private static boolean combinedResultAreTrue(boolean... inputs) {
    int count = 0;
    for (boolean input : inputs) {
      count += (input ? 1 : 0);
    }
    return count % 2 != 0;
  }

  private <T> boolean matches(
      MatchDifference.Field field, MatchDifference context, Matcher<T> matcher, T t) {
    if (context != null) {
      context.currentField(field);
    }
    boolean result = false;

    if (matcher == null) {
      result = true;
    } else if (matcher.matches(context, t)) {
      result = true;
    }

    return result;
  }

  @Override
  public String toString() {
    try {
      return TO_STRING_OBJECT_WRITER.writeValueAsString(httpRequest);
    } catch (Exception e) {
      return super.toString();
    }
  }

  @Override
  @JsonIgnore
  public String[] fieldsExcludedFromEqualsAndHashCode() {
    return excludedFields;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (hashCode() != o.hashCode()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    HttpRequestPropertiesMatcher that = (HttpRequestPropertiesMatcher) o;
    return Objects.equals(httpRequest, that.httpRequest);
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      hashCode = Objects.hash(super.hashCode(), httpRequest);
    }
    return hashCode;
  }
}
