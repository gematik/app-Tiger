/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.mockserver.mock.action.http;

import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.HttpResponse;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/*
 * @author jamesdbloom
 */
public class HttpForwardActionResult {
  private final HttpRequest httpRequest;
  private final InetSocketAddress remoteAddress;
  private CompletableFuture<HttpResponse> httpResponse;
  private final Function<HttpResponse, HttpResponse> overrideHttpResponse;
  private final AtomicBoolean overrideHttpResponseApplied = new AtomicBoolean(false);

  public HttpForwardActionResult(
      HttpRequest httpRequest,
      CompletableFuture<HttpResponse> httpResponse,
      Function<HttpResponse, HttpResponse> overrideHttpResponse) {
    this(httpRequest, httpResponse, overrideHttpResponse, null);
  }

  HttpForwardActionResult(
      HttpRequest httpRequest,
      CompletableFuture<HttpResponse> httpResponse,
      Function<HttpResponse, HttpResponse> overrideHttpResponse,
      InetSocketAddress remoteAddress) {
    this.httpRequest = httpRequest;
    this.httpResponse = httpResponse;
    this.overrideHttpResponse = overrideHttpResponse;
    this.remoteAddress = remoteAddress;
  }

  public HttpRequest getHttpRequest() {
    return httpRequest;
  }

  public CompletableFuture<HttpResponse> getHttpResponse() {
    if (overrideHttpResponse == null) {
      return httpResponse;
    }
    if (overrideHttpResponseApplied.compareAndSet(false, true)) {
      httpResponse =
          httpResponse.thenApply(
              response -> {
                if (response != null) {
                  return overrideHttpResponse.apply(response);
                } else {
                  return null;
                }
              });
    }
    return httpResponse;
  }

  public HttpForwardActionResult setHttpResponse(CompletableFuture<HttpResponse> httpResponse) {
    this.httpResponse = httpResponse;
    return this;
  }

  public InetSocketAddress getRemoteAddress() {
    return remoteAddress;
  }
}
