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

package de.gematik.test.tiger.mockserver.mock.action.http;

import static de.gematik.test.tiger.mockserver.model.HttpResponse.notFoundResponse;

import de.gematik.test.tiger.mockserver.filters.HopByHopHeaderFilter;
import de.gematik.test.tiger.mockserver.httpclient.HttpRequestInfo;
import de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.HttpResponse;
import io.netty.channel.Channel;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Slf4j
public class HttpForwardActionHandler {

  private final NettyHttpClient httpClient;
  private final HopByHopHeaderFilter hopByHopHeaderFilter = new HopByHopHeaderFilter();

  HttpForwardActionHandler(NettyHttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public HttpForwardActionResult sendRequest(
      HttpRequest request,
      Channel incomingChannel,
      @Nullable InetSocketAddress remoteAddress,
      UnaryOperator<HttpResponse> overrideHttpResponse) {
    try {
      // TODO(jamesdbloom) support proxying via HTTP2, for now always force into HTTP1
      return new HttpForwardActionResult(
          request,
          httpClient.sendRequest(
              new HttpRequestInfo(
                  incomingChannel,
                  hopByHopHeaderFilter.onRequest(request).setProtocol(null),
                  remoteAddress)),
          overrideHttpResponse,
          remoteAddress);
    } catch (Exception e) {
      log.error("exception forwarding request {}", request, e);
    }
    return notFoundFuture(request);
  }

  HttpForwardActionResult notFoundFuture(HttpRequest httpRequest) {
    CompletableFuture<HttpResponse> notFoundFuture = new CompletableFuture<>();
    notFoundFuture.complete(notFoundResponse());
    return new HttpForwardActionResult(httpRequest, notFoundFuture, null);
  }
}
