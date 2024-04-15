/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.mock;

import static de.gematik.test.tiger.mockserver.model.HttpResponse.notFoundResponse;

import de.gematik.test.tiger.mockserver.mock.action.ExpectationForwardAndResponseCallback;
import de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler;
import de.gematik.test.tiger.mockserver.mock.action.http.HttpForwardActionResult;
import de.gematik.test.tiger.mockserver.model.*;
import de.gematik.test.tiger.mockserver.netty.responsewriter.NettyResponseWriter;
import java.util.concurrent.CompletableFuture;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Data
@Slf4j
@Accessors(chain = true)
public class HttpAction {

  private final Action action;
  private ExpectationForwardAndResponseCallback expectationForwardAndResponseCallback;

  public static HttpAction of(Action actionToTake) {
    return new HttpAction(actionToTake);
  }

  public void handle(
      HttpRequest request,
      HttpActionHandler actionHandler,
      NettyResponseWriter responseWriter,
      boolean synchronous) {
    final HttpRequest overriddenRequest = getOverridenRequest(request);

    if (action instanceof HttpOverrideForwardedRequest) {
      final HttpForwardActionResult responseFuture =
          actionHandler.getHttpForwardActionHandler().sendRequest(overriddenRequest, null, null);

      actionHandler.executeAfterForwardActionResponse(
          responseFuture,
          (httpResponse, exception) -> {
            if (httpResponse != null) {
              try {
                HttpResponse callbackResponse =
                    expectationForwardAndResponseCallback.handle(overriddenRequest, httpResponse);
                actionHandler.writeForwardActionResponse(callbackResponse, responseWriter, request);
              } catch (Exception e) {
                log.warn("returning error because client response callback threw an exception", e);
                actionHandler.writeForwardActionResponse(
                    notFoundFuture(request), responseWriter, request, action, synchronous);
              }
            } else if (exception != null) {
              actionHandler.handleExceptionDuringForwardingRequest(
                  expectationForwardAndResponseCallback.handleException(exception, request),
                  request,
                  responseWriter,
                  exception);
            }
          },
          synchronous);
    }
  }

  private static HttpForwardActionResult notFoundFuture(HttpRequest httpRequest) {
    CompletableFuture<HttpResponse> notFoundFuture = new CompletableFuture<>();
    notFoundFuture.complete(notFoundResponse());
    return new HttpForwardActionResult(httpRequest, notFoundFuture, null);
  }

  private HttpRequest getOverridenRequest(HttpRequest request) {
    HttpRequest overriddenRequest = request;
    if (expectationForwardAndResponseCallback != null) {
      overriddenRequest = expectationForwardAndResponseCallback.handle(request);
    }
    return overriddenRequest;
  }
}
