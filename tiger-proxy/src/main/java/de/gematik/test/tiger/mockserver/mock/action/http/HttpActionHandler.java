/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.mock.action.http;

import static de.gematik.test.tiger.mockserver.character.Character.NEW_LINE;
import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.*;
import static de.gematik.test.tiger.mockserver.model.HttpResponse.notFoundResponse;
import static de.gematik.test.tiger.mockserver.model.HttpResponse.response;
import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import de.gematik.test.tiger.mockserver.configuration.Configuration;
import de.gematik.test.tiger.mockserver.filters.HopByHopHeaderFilter;
import de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient;
import de.gematik.test.tiger.mockserver.httpclient.SocketCommunicationException;
import de.gematik.test.tiger.mockserver.mock.Expectation;
import de.gematik.test.tiger.mockserver.mock.HttpAction;
import de.gematik.test.tiger.mockserver.mock.HttpState;
import de.gematik.test.tiger.mockserver.model.*;
import de.gematik.test.tiger.mockserver.netty.responsewriter.NettyResponseWriter;
import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration;
import de.gematik.test.tiger.mockserver.scheduler.Scheduler;
import de.gematik.test.tiger.mockserver.socket.tls.NettySslContextFactory;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.base64.Base64;
import io.netty.util.AttributeKey;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;

/*
 * @author jamesdbloom
 */
@SuppressWarnings({"rawtypes", "FieldMayBeFinal"})
@Slf4j
public class HttpActionHandler {

  public static final AttributeKey<InetSocketAddress> REMOTE_SOCKET =
      AttributeKey.valueOf("REMOTE_SOCKET");

  private final Configuration configuration;
  private final HttpState httpStateHandler;
  private final Scheduler scheduler;
  private HttpForwardActionHandler httpForwardActionHandler;

  // forwarding
  @Getter private NettyHttpClient httpClient;
  private HopByHopHeaderFilter hopByHopHeaderFilter = new HopByHopHeaderFilter();

  public HttpActionHandler(
      Configuration configuration,
      EventLoopGroup eventLoopGroup,
      HttpState httpStateHandler,
      List<ProxyConfiguration> proxyConfigurations,
      NettySslContextFactory nettySslContextFactory) {
    this.configuration = configuration;
    this.httpStateHandler = httpStateHandler;
    this.scheduler = httpStateHandler.getScheduler();
    this.httpClient =
        new NettyHttpClient(
            configuration, eventLoopGroup, proxyConfigurations, true, nettySslContextFactory);
  }

  public void processAction(
      final HttpRequest request,
      final NettyResponseWriter responseWriter,
      final ChannelHandlerContext ctx,
      Set<String> localAddresses,
      boolean proxyingRequest,
      final boolean synchronous) {
    if (request.getHeaders() == null
        || !request
            .getHeaders()
            .containsEntry(
                httpStateHandler.getUniqueLoopPreventionHeaderName(),
                httpStateHandler.getUniqueLoopPreventionHeaderValue())) {
      log.debug("received request:{}", request);
    }
    final Expectation expectation = httpStateHandler.firstMatchingExpectation(request);
    final boolean potentiallyHttpProxy =
        !proxyingRequest
            && configuration.attemptToProxyIfNoMatchingExpectation()
            && !isEmpty(request.getFirstHeader(HOST.toString()))
            && !localAddresses.contains(request.getFirstHeader(HOST.toString()));

    if (expectation != null && expectation.getHttpAction() != null) {
      final HttpAction action = expectation.getHttpAction();
      scheduler.schedule(
          () -> action.handle(request, this, responseWriter, synchronous), synchronous);
    } else if (proxyingRequest || potentiallyHttpProxy) {

      if (request.getHeaders() != null
          && request
              .getHeaders()
              .containsEntry(
                  httpStateHandler.getUniqueLoopPreventionHeaderName(),
                  httpStateHandler.getUniqueLoopPreventionHeaderValue())) {

        log.trace(
            "received \"x-forwarded-by\" header caused by exploratory HTTP proxy or proxy"
                + " loop - falling back to no proxy:{}",
            request);
        returnNotFound(responseWriter, request, null);
      } else {
        String username = configuration.proxyAuthenticationUsername();
        String password = configuration.proxyAuthenticationPassword();
        // only authenticate potentiallyHttpProxy because other proxied requests should have already
        // been authenticated (i.e. in CONNECT request)
        if (potentiallyHttpProxy
            && isNotBlank(username)
            && isNotBlank(password)
            && !request.containsHeader(
                PROXY_AUTHORIZATION.toString(),
                "Basic "
                    + Base64.encode(
                            Unpooled.copiedBuffer(
                                username + ':' + password, StandardCharsets.UTF_8),
                            false)
                        .toString(StandardCharsets.US_ASCII))) {

          HttpResponse response =
              response()
                  .withStatusCode(PROXY_AUTHENTICATION_REQUIRED.code())
                  .withHeader(
                      PROXY_AUTHENTICATE.toString(),
                      "Basic realm=\""
                          + StringEscapeUtils.escapeJava(configuration.proxyAuthenticationRealm())
                          + "\", charset=\"UTF-8\"");
          responseWriter.writeResponse(request, response);
          log.debug(
              "proxy authentication failed so returning response:{}for forwarded" + " request:{}",
              response,
              request);

        } else {

          final InetSocketAddress remoteAddress = getRemoteAddress(ctx);
          final HttpRequest clonedRequest =
              hopByHopHeaderFilter
                  .onRequest(request)
                  .withHeader(
                      httpStateHandler.getUniqueLoopPreventionHeaderName(),
                      httpStateHandler.getUniqueLoopPreventionHeaderValue());
          final HttpForwardActionResult responseFuture =
              new HttpForwardActionResult(
                  clonedRequest,
                  httpClient.sendRequest(
                      clonedRequest,
                      remoteAddress,
                      potentiallyHttpProxy
                          ? 1000
                          : configuration.socketConnectionTimeoutInMillis()),
                  null,
                  remoteAddress);
          scheduler.submit(
              responseFuture,
              () -> {
                try {
                  HttpResponse response =
                      responseFuture
                          .getHttpResponse()
                          .get(configuration.maxFutureTimeoutInMillis(), MILLISECONDS);
                  if (response == null) {
                    response = notFoundResponse();
                  }
                  if (response.containsHeader(
                      httpStateHandler.getUniqueLoopPreventionHeaderName(),
                      httpStateHandler.getUniqueLoopPreventionHeaderValue())) {
                    response.removeHeader(httpStateHandler.getUniqueLoopPreventionHeaderName());
                    log.debug("no expectation for:{}returning response:{}", request, response);
                  } else {
                    log.debug(
                        "returning response:{}\nfor forwarded request"
                            + NEW_LINE
                            + NEW_LINE
                            + " in json:{}",
                        request,
                        response);
                  }
                  responseWriter.writeResponse(request, response);
                } catch (SocketCommunicationException sce) {
                  log.warn("Exception while writing response", sce);
                  returnNotFound(responseWriter, request, sce.getMessage());
                } catch (InterruptedException | ExecutionException | TimeoutException throwable) {
                  if (throwable instanceof TimeoutException) {
                    Thread.currentThread().interrupt();
                  }
                  if (potentiallyHttpProxy && connectionException(throwable)) {
                    log.error(
                        "failed to connect to proxied socket due to exploratory HTTP proxy for:{}"
                            + " due to:{} falling back to no proxy",
                        request,
                        throwable.getCause());
                    returnNotFound(responseWriter, request, null);
                  } else if (sslHandshakeException(throwable)) {
                    log.error(
                        "TLS handshake exception while proxying request {} to remote address {}"
                            + " with channel {}",
                        (ctx != null ? String.valueOf(ctx.channel()) : ""),
                        request,
                        remoteAddress);
                    returnNotFound(
                        responseWriter,
                        request,
                        "TLS handshake exception while proxying request to remote address"
                            + remoteAddress);
                  } else if (!connectionClosedException(throwable)) {
                    log.error(
                        "connection closed while proxying request to remote address {}",
                        remoteAddress,
                        throwable);
                    returnNotFound(
                        responseWriter,
                        request,
                        "connection closed while proxying request to remote address"
                            + remoteAddress);
                  } else {
                    log.error("Exception while proxying request", throwable);
                    returnNotFound(responseWriter, request, throwable.getMessage());
                  }
                }
              },
              synchronous,
              throwable ->
                  !(potentiallyHttpProxy && isNotBlank(throwable.getMessage())
                      || !throwable.getMessage().contains("Connection refused")));
        }
      }

    } else {
      log.error("Returning not found!");
      returnNotFound(responseWriter, request, null);
    }
  }

  public void executeAfterForwardActionResponse(
      final HttpForwardActionResult responseFuture,
      final BiConsumer<HttpResponse, Throwable> command,
      final boolean synchronous) {
    scheduler.submit(responseFuture, command, synchronous);
  }

  public void writeForwardActionResponse(
      final HttpForwardActionResult responseFuture,
      final NettyResponseWriter responseWriter,
      final HttpRequest request,
      final Action action,
      boolean synchronous) {
    scheduler.submit(
        responseFuture,
        () -> {
          try {
            HttpResponse response =
                responseFuture
                    .getHttpResponse()
                    .get(configuration.maxFutureTimeoutInMillis(), MILLISECONDS);
            responseWriter.writeResponse(request, response);
            log.debug(
                "returning response:{}for forwarded request"
                    + NEW_LINE
                    + NEW_LINE
                    + " in json:{}"
                    + NEW_LINE
                    + NEW_LINE
                    + "\nfor action:{}from expectation:{}",
                response,
                responseFuture.getHttpRequest(),
                action,
                action.getExpectationId());
          } catch (RuntimeException
              | InterruptedException
              | ExecutionException
              | TimeoutException throwable) {
            if (throwable instanceof InterruptedException) {
              Thread.currentThread().interrupt();
            }
            handleExceptionDuringForwardingRequest(action, request, responseWriter, throwable);
          }
        },
        synchronous,
        throwable -> true);
  }

  public void writeForwardActionResponse(
      final HttpResponse response, final NettyResponseWriter responseWriter, final HttpRequest request) {
    try {
      responseWriter.writeResponse(request, response);
      log.debug(
          "returning response:{}for forwarded request" + NEW_LINE + NEW_LINE + " in json:{}",
          response,
          request);
    } catch (Exception exception) {
      log.error("Error while returning response", exception);
    }
  }

  public void handleExceptionDuringForwardingRequest(
      Action action, HttpRequest request, NettyResponseWriter responseWriter, Throwable exception) {
    if (action instanceof CloseChannel) {
      log.debug("closing channel due to close action");
      responseWriter.closeChannel();
    } else {
      if (connectionException(exception)) {
        log.error(
            "failed to connect to remote socket while forwarding request {} for action {}",
            request,
            action,
            exception);
        returnNotFound(
            responseWriter, request, "failed to connect to remote socket while forwarding request");
      } else if (sslHandshakeException(exception)) {
        log.error(
            "TLS handshake exception while forwarding request {} for action {}",
            request,
            action,
            exception);
        returnNotFound(responseWriter, request, "TLS handshake exception while forwarding request");
      } else {
        log.error("Exception while forwarding request", exception);
        returnNotFound(responseWriter, request, exception != null ? exception.getMessage() : null);
      }
    }
  }

  private void returnNotFound(NettyResponseWriter responseWriter, HttpRequest request, String error) {
    HttpResponse response = notFoundResponse();
    if (request.getHeaders() != null
        && request
            .getHeaders()
            .containsEntry(
                httpStateHandler.getUniqueLoopPreventionHeaderName(),
                httpStateHandler.getUniqueLoopPreventionHeaderValue())) {
      response.withHeader(
          httpStateHandler.getUniqueLoopPreventionHeaderName(),
          httpStateHandler.getUniqueLoopPreventionHeaderValue());
      log.trace("no expectation for:{}returning response:{}", request, notFoundResponse());
    } else if (isNotBlank(error)) {
      log.debug("error:{}handling request:{}returning response:{}", error, request, notFoundResponse());
    } else {
      log.debug("no expectation for:{}returning response:{}", request, notFoundResponse());
    }
    responseWriter.writeResponse(request, response);
  }

  public HttpForwardActionHandler getHttpForwardActionHandler() {
    if (httpForwardActionHandler == null) {
      httpForwardActionHandler = new HttpForwardActionHandler(httpClient);
    }
    return httpForwardActionHandler;
  }

  public static InetSocketAddress getRemoteAddress(final ChannelHandlerContext ctx) {
    if (ctx != null && ctx.channel() != null && ctx.channel().attr(REMOTE_SOCKET) != null) {
      return ctx.channel().attr(REMOTE_SOCKET).get();
    } else {
      return null;
    }
  }
}
