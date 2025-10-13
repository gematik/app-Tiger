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

import static de.gematik.test.tiger.mockserver.character.Character.NEW_LINE;
import static de.gematik.test.tiger.mockserver.model.HttpResponse.notFoundResponse;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import de.gematik.rbellogger.util.RbelInternetAddressParser;
import de.gematik.rbellogger.util.RbelSocketAddress;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.filters.HopByHopHeaderFilter;
import de.gematik.test.tiger.mockserver.httpclient.HttpRequestInfo;
import de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient;
import de.gematik.test.tiger.mockserver.httpclient.SocketCommunicationException;
import de.gematik.test.tiger.mockserver.mock.Expectation;
import de.gematik.test.tiger.mockserver.mock.HttpAction;
import de.gematik.test.tiger.mockserver.mock.HttpState;
import de.gematik.test.tiger.mockserver.model.*;
import de.gematik.test.tiger.mockserver.netty.responsewriter.NettyResponseWriter;
import de.gematik.test.tiger.mockserver.scheduler.Scheduler;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyRoutingException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/*
 * @author jamesdbloom
 */
@SuppressWarnings({"rawtypes", "FieldMayBeFinal"})
@Slf4j
public class HttpActionHandler {

  public static final AttributeKey<InetSocketAddress> REMOTE_SOCKET =
      AttributeKey.valueOf("REMOTE_SOCKET");

  private final MockServerConfiguration configuration;
  private final HttpState httpStateHandler;
  private final Scheduler scheduler;
  private HttpForwardActionHandler httpForwardActionHandler;

  // forwarding
  @Getter private NettyHttpClient httpClient;
  private HopByHopHeaderFilter hopByHopHeaderFilter = new HopByHopHeaderFilter();

  public HttpActionHandler(
      MockServerConfiguration configuration,
      HttpState httpStateHandler,
      NettyHttpClient nettyHttp) {
    this.configuration = configuration;
    this.httpStateHandler = httpStateHandler;
    this.scheduler = httpStateHandler.getScheduler();
    this.httpClient = nettyHttp;
  }

  public void processAction(
      final HttpRequest request,
      final NettyResponseWriter responseWriter,
      final ChannelHandlerContext ctx,
      boolean proxyingRequest,
      final boolean synchronous) {
    final Expectation expectation = httpStateHandler.firstMatchingExpectation(request);

    if (expectation != null && expectation.getHttpAction() != null) {
      final HttpAction action = expectation.getHttpAction();
      scheduler.schedule(
          () -> action.handle(request, ctx.channel(), this, responseWriter, synchronous));
    } else {
      if (!proxyingRequest) {
        log.info("No route found for {}", request.printLogLineDescription());
        closeChannelWithErrorMessage(
            responseWriter,
            request,
            new TigerProxyRoutingException(
                "No route found", RbelSocketAddress.create(getRemoteAddress(ctx)), null, null));
      } else {
        final InetSocketAddress remoteAddress = getRemoteAddress(ctx);
        final HttpRequest clonedRequest = hopByHopHeaderFilter.onRequest(request);
        final HttpForwardActionResult responseFuture =
            new HttpForwardActionResult(
                clonedRequest,
                httpClient.sendRequest(
                    new HttpRequestInfo(ctx.channel(), clonedRequest, remoteAddress),
                    configuration.socketConnectionTimeoutInMillis()),
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
                log.debug(
                    "returning response:{}\nfor forwarded request"
                        + NEW_LINE
                        + NEW_LINE
                        + " in json:{}",
                    request,
                    response);
                responseWriter.writeResponse(request, response);
              } catch (SocketCommunicationException
                  | InterruptedException
                  | ExecutionException
                  | TimeoutException e) {
                if (e instanceof TimeoutException) {
                  Thread.currentThread().interrupt();
                }

                closeChannelWithErrorMessage(responseWriter, request, e, remoteAddress);
              }
            },
            synchronous,
            throwable -> throwable.getMessage().contains("Connection refused"));
      }
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
            log.atDebug()
                .addArgument(response)
                .addArgument(responseFuture::getHttpRequest)
                .addArgument(action)
                .addArgument(action::getExpectationId)
                .log(
                    "returning response: {} for forwarded request"
                        + NEW_LINE
                        + NEW_LINE
                        + " in json: {}"
                        + NEW_LINE
                        + NEW_LINE
                        + "\nfor action: {} from expectation: {}");
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
      final HttpResponse response,
      final NettyResponseWriter responseWriter,
      final HttpRequest request) {
    try {
      responseWriter.writeResponse(request, response);
      log.debug(
          "returning response: {} for forwarded request" + NEW_LINE + NEW_LINE + " in json:{}",
          response,
          request);
    } catch (Exception exception) {
      log.error("Error while returning response", exception);
      closeChannelWithErrorMessage(responseWriter, request, exception);
    }
  }

  public void handleExceptionDuringForwardingRequest(
      Action action, HttpRequest request, NettyResponseWriter responseWriter, Throwable exception) {
    if (action instanceof CloseChannel) {
      log.debug("closing channel due to close action");
      responseWriter.closeChannel();
    } else {
      closeChannelWithErrorMessage(responseWriter, request, exception);
    }
  }

  private void closeChannelWithErrorMessage(
      NettyResponseWriter responseWriter,
      HttpRequest request,
      Throwable error,
      InetSocketAddress... remoteAddress) {
    log.debug(
        "error: {} handling request: {} returning response: {}",
        error,
        request,
        notFoundResponse());
    val ctx = responseWriter.getCtx();
    if (configuration.exceptionHandlingCallback() != null && ctx.channel().isOpen()) {
      if (error instanceof TigerProxyRoutingException routingException) {
        configuration.exceptionHandlingCallback().accept(routingException, ctx);
      } else {
        RbelSocketAddress senderAddress = null;
        if (remoteAddress.length > 0) {
          senderAddress = RbelSocketAddress.create(remoteAddress[0]);
        }
        val routingException =
            new TigerProxyRoutingException(error.getMessage(), senderAddress, null, error);
        configuration.exceptionHandlingCallback().accept(routingException, ctx);
      }
    }
    responseWriter.closeChannel();
  }

  public HttpForwardActionHandler getHttpForwardActionHandler() {
    if (httpForwardActionHandler == null) {
      httpForwardActionHandler = new HttpForwardActionHandler(httpClient);
    }
    return httpForwardActionHandler;
  }

  public static InetSocketAddress getRemoteAddress(final ChannelHandlerContext ctx) {
    if (ctx != null && ctx.channel() != null && ctx.channel().attr(REMOTE_SOCKET) != null) {
      var remoteSocket = ctx.channel().attr(REMOTE_SOCKET).get();
      final SocketAddress localAddress = ctx.channel().localAddress();
      if (remoteSocket != null) {
        if (remoteSocket.getAddress() != null
            && remoteSocket.getAddress().isLoopbackAddress()
            && localAddress instanceof InetSocketAddress localInetSocketAddress) {
          return new InetSocketAddress(localInetSocketAddress.getAddress(), remoteSocket.getPort());
        } else {
          return RbelInternetAddressParser.parseInetAddress(remoteSocket.toString())
              .toInetAddress()
              .map(inetAdr -> new InetSocketAddress(inetAdr, remoteSocket.getPort()))
              .orElse(remoteSocket);
        }
      }
      return remoteSocket;
    } else {
      return null;
    }
  }
}
