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
package de.gematik.test.tiger.mockserver.netty;

import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.closeOnFlush;
import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.connectionClosedException;
import static de.gematik.test.tiger.mockserver.model.HttpResponse.response;
import static de.gematik.test.tiger.mockserver.netty.unification.PortUnificationHandler.enableSslUpstreamAndDownstream;
import static de.gematik.test.tiger.mockserver.netty.unification.PortUnificationHandler.isSslEnabledUpstream;
import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.mock.HttpState;
import de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.netty.proxy.connect.HttpConnectHandler;
import de.gematik.test.tiger.mockserver.netty.responsewriter.NettyResponseWriter;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
@Slf4j
public class HttpRequestHandler extends SimpleChannelInboundHandler<HttpRequest> {

  public static final AttributeKey<Boolean> PROXYING = AttributeKey.valueOf("PROXYING");
  public static final AttributeKey<Set<String>> LOCAL_HOST_HEADERS =
      AttributeKey.valueOf("LOCAL_HOST_HEADERS");
  private HttpState httpState;
  private final MockServerConfiguration configuration;
  private MockServer server;
  private HttpActionHandler httpActionHandler;

  public HttpRequestHandler(
      MockServerConfiguration configuration,
      MockServer server,
      HttpState httpState,
      HttpActionHandler httpActionHandler) {
    super(false);
    this.configuration = configuration;
    this.server = server;
    this.httpState = httpState;
    this.httpActionHandler = httpActionHandler;
  }

  private static boolean isProxyingRequest(ChannelHandlerContext ctx) {
    if (ctx != null && ctx.channel().attr(PROXYING).get() != null) {
      return ctx.channel().attr(PROXYING).get();
    }
    return false;
  }

  @Override
  protected void channelRead0(final ChannelHandlerContext ctx, final HttpRequest request) {
    NettyResponseWriter responseWriter = new NettyResponseWriter(configuration, ctx);
    try {
      configuration.addSubjectAlternativeName(request.getFirstHeader(HOST.toString()));

      if (!httpState.handle(request)) {
        if (request.getMethod().equals("CONNECT")) {
          openProxyChannel(ctx, request);
        } else {
          httpActionHandler.processAction(
              request, responseWriter, ctx, isProxyingRequest(ctx), false);
        }
      }
    } catch (Exception ex) {
      log.error("exception processing {}", request, ex);
      responseWriter.writeResponse(
          request,
          response()
              .withStatusCode(BAD_REQUEST.code())
              .withBody(
                  Optional.ofNullable(ex.getMessage())
                      .map(e -> e.getBytes(StandardCharsets.UTF_8))
                      .orElse("<null>".getBytes())));
    }
  }

  private void openProxyChannel(ChannelHandlerContext ctx, HttpRequest request) {
    ctx.channel().attr(PROXYING).set(Boolean.TRUE);
    // assume SSL for CONNECT request
    enableSslUpstreamAndDownstream(ctx.channel());
    // add Subject Alternative Name for SSL certificate
    log.info("Opening Proxy Channel, enabling SSL for {}", request.getPath());
    if (isNotBlank(request.getPath())) {
      server
          .getScheduler()
          .submit(() -> configuration.addSubjectAlternativeName(request.getPath()));
    }
    String[] hostParts = request.getPath().split(":");
    final int port = determinePort(ctx, hostParts);
    ctx.pipeline().addLast(new HttpConnectHandler(configuration, server, hostParts[0], port));
    ctx.pipeline().remove(this);
    ctx.fireChannelRead(request);
  }

  private static int determinePort(ChannelHandlerContext ctx, String[] hostParts) {
    int port;
    if (hostParts.length > 1) {
      port = Integer.parseInt(hostParts[1]);
    } else {
      if (isSslEnabledUpstream(ctx.channel())) port = 443;
      else port = 80;
    }
    return port;
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) {
    ctx.flush();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    if (connectionClosedException(cause)) {
      log.error(
          "exception caught by {} handler -> closing pipeline {}",
          server.getClass(),
          ctx.channel(),
          cause);
    }
    closeOnFlush(ctx.channel());
  }
}
