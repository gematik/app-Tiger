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

  private final Action<?> action;
  private final Delay delay;
  private ExpectationForwardAndResponseCallback expectationForwardAndResponseCallback;

  public static HttpAction of(Action<?> actionToTake) {
    return new HttpAction(actionToTake, Delay.NONE);
  }

  public static HttpAction of(Action<?> actionToTake, Delay delay) {
    return new HttpAction(actionToTake, delay);
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
