/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.mock;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.apache.commons.lang3.StringUtils.*;

import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.model.*;
import de.gematik.test.tiger.mockserver.scheduler.Scheduler;
import de.gematik.test.tiger.mockserver.uuid.UUIDService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
  // mockserver
  private final MockServerConfiguration configuration;
  private List<Expectation> expectations = new ArrayList<>();

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

  public void reset() {
    log.info("resetting all expectations and request logs");
  }

  public List<Expectation> add(Expectation... expectations) {
    for (Expectation expectation : expectations) {
      this.expectations.add(expectation);

      final String hostHeader = expectation.getRequestPattern().getFirstHeader(HOST.toString());
      if (isNotBlank(hostHeader)) {
        scheduler.submit(() -> configuration.addSubjectAlternativeName(hostHeader));
      }
    }
    return List.of();
  }

  public Expectation firstMatchingExpectation(HttpRequest request) {
    return expectations.stream()
        .filter(expectation -> expectation.matches(request))
        .min(Comparator.naturalOrder())
        .orElse(null);
  }

  public boolean handle(HttpRequest request) {
    request.withLogCorrelationId(UUIDService.getUUID());
    setPort(request);

    log.trace("received request:{}", request);

    return false;
  }

  public String getUniqueLoopPreventionHeaderName() {
    return "x-forwarded-by";
  }

  public List<Expectation> retrieveActiveExpectations() {
    return expectations;
  }

  public void clear(String expectationId) {
    boolean foundRoute =
        expectations.removeIf(expectation -> expectation.getId().equals(expectationId));
    log.info("removed expectation with id [{}]: {}", expectationId, foundRoute);
  }
}
