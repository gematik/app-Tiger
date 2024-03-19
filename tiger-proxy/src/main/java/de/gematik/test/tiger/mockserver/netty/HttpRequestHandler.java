/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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

import de.gematik.test.tiger.mockserver.configuration.Configuration;
import de.gematik.test.tiger.mockserver.mock.HttpState;
import de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.HttpResponse;
import de.gematik.test.tiger.mockserver.model.MediaType;
import de.gematik.test.tiger.mockserver.netty.proxy.connect.HttpConnectHandler;
import de.gematik.test.tiger.mockserver.netty.responsewriter.NettyResponseWriter;
import de.gematik.test.tiger.mockserver.responsewriter.ResponseWriter;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;

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
  private final Configuration configuration;
  private MockServer server;
  private HttpActionHandler httpActionHandler;

  public HttpRequestHandler(
      Configuration configuration,
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

  private static Set<String> getLocalAddresses(ChannelHandlerContext ctx) {
    if (ctx != null
        && ctx.channel().attr(LOCAL_HOST_HEADERS) != null
        && ctx.channel().attr(LOCAL_HOST_HEADERS).get() != null) {
      return ctx.channel().attr(LOCAL_HOST_HEADERS).get();
    }
    return new HashSet<>();
  }

  @Override
  protected void channelRead0(final ChannelHandlerContext ctx, final HttpRequest request) {

    ResponseWriter responseWriter = new NettyResponseWriter(configuration, ctx);
    try {
      configuration.addSubjectAlternativeName(request.getFirstHeader(HOST.toString()));

      if (!httpState.handle(request)) {
        if (request.getMethod().equals("CONNECT")) {

          String username = configuration.proxyAuthenticationUsername();
          String password = configuration.proxyAuthenticationPassword();
          if (isNotBlank(username)
              && isNotBlank(password)
              && !request.containsHeader(
                  PROXY_AUTHORIZATION.toString(),
                  "Basic "
                      + Base64.getEncoder()
                          .encodeToString(
                              (username + ':' + password).getBytes(StandardCharsets.UTF_8)))) {
            HttpResponse response =
                response()
                    .withStatusCode(PROXY_AUTHENTICATION_REQUIRED.code())
                    .withHeader(
                        PROXY_AUTHENTICATE.toString(),
                        "Basic realm=\""
                            + StringEscapeUtils.escapeJava(configuration.proxyAuthenticationRealm())
                            + "\", charset=\"UTF-8\"");
            ctx.writeAndFlush(response);
            log.info(
                "proxy authentication failed so returning response:{}\n"
                    + "for forwarded request:{}",
                response,
                request);
          } else {
            ctx.channel().attr(PROXYING).set(Boolean.TRUE);
            // assume SSL for CONNECT request
            enableSslUpstreamAndDownstream(ctx.channel());
            // add Subject Alternative Name for SSL certificate
            if (isNotBlank(request.getPath())) {
              server
                  .getScheduler()
                  .submit(() -> configuration.addSubjectAlternativeName(request.getPath()));
            }
            String[] hostParts = request.getPath().split(":");
            final int port = determinePort(ctx, hostParts);
            ctx.pipeline()
                .addLast(new HttpConnectHandler(configuration, server, hostParts[0], port));
            ctx.pipeline().remove(this);
            ctx.fireChannelRead(request);
          }
        } else {
          try {
            httpActionHandler.processAction(
                request,
                responseWriter,
                ctx,
                getLocalAddresses(ctx),
                isProxyingRequest(ctx),
                false);
          } catch (RuntimeException e) {
            log.error("exception processing request '{}'", request, e);
          }
        }
      }
    } catch (IllegalArgumentException iae) {
      log.error("exception processing request:{}error:{}", request, iae.getMessage());
      // send request without API CORS headers
      responseWriter.writeResponse(
          request, BAD_REQUEST, iae.getMessage(), MediaType.create("text", "plain").toString());
    } catch (Exception ex) {
      log.error("exception processing ", request, ex);
      responseWriter.writeResponse(
          request, response().withStatusCode(BAD_REQUEST.code()).withBody(ex.getMessage()), true);
    }
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
