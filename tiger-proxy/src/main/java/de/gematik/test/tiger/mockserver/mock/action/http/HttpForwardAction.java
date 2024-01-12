package de.gematik.test.tiger.mockserver.mock.action.http;

import static de.gematik.test.tiger.mockserver.model.HttpResponse.notFoundResponse;

import de.gematik.test.tiger.mockserver.filters.HopByHopHeaderFilter;
import de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient;
import de.gematik.test.tiger.mockserver.log.model.LogEntry;
import de.gematik.test.tiger.mockserver.logging.MockServerLogger;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.HttpResponse;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.slf4j.event.Level;

@SuppressWarnings("FieldMayBeFinal")
public abstract class HttpForwardAction {

  protected final MockServerLogger mockServerLogger;
  private final NettyHttpClient httpClient;
  private HopByHopHeaderFilter hopByHopHeaderFilter = new HopByHopHeaderFilter();

  HttpForwardAction(MockServerLogger mockServerLogger, NettyHttpClient httpClient) {
    this.mockServerLogger = mockServerLogger;
    this.httpClient = httpClient;
  }

  public HttpForwardActionResult sendRequest(
      HttpRequest request,
      @Nullable InetSocketAddress remoteAddress,
      Function<HttpResponse, HttpResponse> overrideHttpResponse) {
    try {
      // TODO(jamesdbloom) support proxying via HTTP2, for now always force into HTTP1
      return new HttpForwardActionResult(
          request,
          httpClient.sendRequest(
              hopByHopHeaderFilter.onRequest(request).withProtocol(null), remoteAddress),
          overrideHttpResponse,
          remoteAddress);
    } catch (Exception e) {
      mockServerLogger.logEvent(
          new LogEntry()
              .setLogLevel(Level.ERROR)
              .setHttpRequest(request)
              .setMessageFormat("exception forwarding request " + request)
              .setThrowable(e));
    }
    return notFoundFuture(request);
  }

  HttpForwardActionResult notFoundFuture(HttpRequest httpRequest) {
    CompletableFuture<HttpResponse> notFoundFuture = new CompletableFuture<>();
    notFoundFuture.complete(notFoundResponse());
    return new HttpForwardActionResult(httpRequest, notFoundFuture, null);
  }
}
