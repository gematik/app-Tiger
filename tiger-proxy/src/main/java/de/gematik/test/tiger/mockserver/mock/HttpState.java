/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.mock;

import static de.gematik.test.tiger.mockserver.log.model.LogEntryMessages.RECEIVED_REQUEST_MESSAGE_FORMAT;
import static de.gematik.test.tiger.mockserver.model.HttpRequest.request;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static org.apache.commons.lang3.StringUtils.*;

import com.google.common.annotations.VisibleForTesting;
import de.gematik.test.tiger.mockserver.configuration.Configuration;
import de.gematik.test.tiger.mockserver.mock.listeners.MockServerMatcherNotifier.Cause;
import de.gematik.test.tiger.mockserver.model.*;
import de.gematik.test.tiger.mockserver.scheduler.Scheduler;
import de.gematik.test.tiger.mockserver.uuid.UUIDService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Slf4j
public class HttpState {

  private static final ThreadLocal<Integer> LOCAL_PORT = new ThreadLocal<>(); // NOSONAR
  private final String uniqueLoopPreventionHeaderValue = "MockServer_" + UUIDService.getUUID();
  private final Scheduler scheduler;
  // mockserver
  private final RequestMatchers requestMatchers;
  private final Configuration configuration;

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

  public HttpState(Configuration configuration, Scheduler scheduler) {
    this.configuration = configuration;
    this.scheduler = scheduler;
    this.requestMatchers = new RequestMatchers(configuration, scheduler);
  }

  public void reset() {
    requestMatchers.reset();
    log.info("resetting all expectations and request logs");
  }

  public List<Expectation> add(Expectation... expectations) {
    List<Expectation> upsertedExpectations = new ArrayList<>();
    for (Expectation expectation : expectations) {
      RequestDefinition requestDefinition = expectation.getHttpRequest();
      if (requestDefinition instanceof HttpRequest) {
        final String hostHeader = ((HttpRequest) requestDefinition).getFirstHeader(HOST.toString());
        if (isNotBlank(hostHeader)) {
          scheduler.submit(() -> configuration.addSubjectAlternativeName(hostHeader));
        }
      }
      upsertedExpectations.add(requestMatchers.add(expectation, Cause.API));
    }
    return upsertedExpectations;
  }

  public Expectation firstMatchingExpectation(HttpRequest request) {
    if (requestMatchers.isEmpty()) {
      return null;
    } else {
      return requestMatchers.firstMatchingExpectation(request);
    }
  }

  @VisibleForTesting
  public List<Expectation> allMatchingExpectation(HttpRequest request) {
    if (requestMatchers.isEmpty()) {
      return Collections.emptyList();
    } else {
      return requestMatchers.retrieveActiveExpectations(request);
    }
  }

  public boolean handle(HttpRequest request) {

    request.withLogCorrelationId(UUIDService.getUUID());
    setPort(request);

    log.trace(RECEIVED_REQUEST_MESSAGE_FORMAT, request);

    return false;
  }

  public RequestMatchers getRequestMatchers() {
    return requestMatchers;
  }

  public Scheduler getScheduler() {
    return scheduler;
  }

  public String getUniqueLoopPreventionHeaderName() {
    return "x-forwarded-by";
  }

  public String getUniqueLoopPreventionHeaderValue() {
    return uniqueLoopPreventionHeaderValue;
  }

  public void stop() {}
}
