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

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.apache.commons.lang3.StringUtils.*;

import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.model.*;
import de.gematik.test.tiger.mockserver.scheduler.Scheduler;
import de.gematik.test.tiger.mockserver.uuid.UUIDService;
import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Slf4j
@Getter
public class HttpState {

  private static final ThreadLocal<Integer> LOCAL_PORT = new ThreadLocal<>(); // NOSONAR
  private final String uniqueLoopPreventionHeaderValue = "MockServer_" + UUIDService.getUUID();
  private final Scheduler scheduler;
  private final MockServerConfiguration configuration;
  private final List<Expectation> expectations = new ArrayList<>();

  public static void setPort(final HttpRequest request) {
    if (request != null && request.getSocketAddress() != null) {
      setPort(request.getSocketAddress().getPort());
      request.setSocketAddress(null);
    }
  }

  public static void clearPort() {
    LOCAL_PORT.remove();
  }

  public static void setPort(final Integer port) {
    LOCAL_PORT.set(port);
  }

  public static void setPort(final Integer... port) {
    if (port != null && port.length > 0) {
      setPort(port[0]);
    }
  }

  public static void setPort(final List<Integer> port) {
    if (port != null && !port.isEmpty()) {
      setPort(port.get(0));
    }
  }

  public static Integer getPort() {
    return LOCAL_PORT.get();
  }

  public HttpState(MockServerConfiguration configuration, Scheduler scheduler) {
    this.configuration = configuration;
    this.scheduler = scheduler;
  }

  public void add(Expectation expectation) {
    this.expectations.add(expectation);

    final String hostHeader = expectation.getRequestPattern().getFirstHeader(HOST.toString());
    if (isNotBlank(hostHeader)) {
      scheduler.submit(() -> configuration.addSubjectAlternativeName(hostHeader));
    }
  }

  public Expectation firstMatchingExpectation(HttpRequest request) {
    log.atDebug().addArgument(request::printLogLineDescription).log("Trying to find route for {}");
    for (Expectation expectation : expectations.stream().sorted().toList()) {
      if (expectation.matches(request)) {
        log.atDebug()
            .addArgument(expectation::createShortDescription)
            .addArgument(request::printLogLineDescription)
            .log("Route {} matched request {}");
        return expectation;
      }
    }
    log.atWarn()
      .addArgument(request::printLogLineDescription)
      .log("No matching route found for request {}");
    return null;
  }

  public boolean handle(HttpRequest request) {
    request.setLogCorrelationId(UUIDService.getUUID());
    setPort(request);

    return false;
  }

  public String getUniqueLoopPreventionHeaderName() {
    return "x-forwarded-by";
  }

  public List<Expectation> retrieveActiveExpectations() {
    return expectations.stream().toList();
  }

  public void clear(String expectationId) {
    boolean foundRoute =
        expectations.removeIf(expectation -> expectation.getId().equals(expectationId));
    log.info("removed expectation with id [{}]: {}", expectationId, foundRoute);
  }
}
