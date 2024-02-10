/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.netty.proxy;

import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.closeOnFlush;
import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.connectionClosedException;
import static de.gematik.test.tiger.mockserver.formatting.StringFormatter.formatBytes;
import static de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler.getRemoteAddress;
import static de.gematik.test.tiger.mockserver.model.BinaryMessage.bytes;
import static de.gematik.test.tiger.mockserver.netty.unification.PortUnificationHandler.isSslEnabledUpstream;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import de.gematik.test.tiger.mockserver.configuration.Configuration;
import de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient;
import de.gematik.test.tiger.mockserver.model.BinaryMessage;
import de.gematik.test.tiger.mockserver.model.BinaryProxyListener;
import de.gematik.test.tiger.mockserver.scheduler.Scheduler;
import de.gematik.test.tiger.mockserver.uuid.UUIDService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
@Slf4j
public class BinaryHandler extends SimpleChannelInboundHandler<ByteBuf> {

  private final Configuration configuration;
  private final Scheduler scheduler;
  private final NettyHttpClient httpClient;
  private final BinaryProxyListener binaryExchangeCallback;

  public BinaryHandler(
      final Configuration configuration,
      final Scheduler scheduler,
      final NettyHttpClient httpClient) {
    super(true);
    this.configuration = configuration;
    this.scheduler = scheduler;
    this.httpClient = httpClient;
    this.binaryExchangeCallback = configuration.binaryProxyListener();
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf) {
    BinaryMessage binaryRequest = bytes(ByteBufUtil.getBytes(byteBuf));
    String logCorrelationId = UUIDService.getUUID();
    log.info("received binary request:{}", ByteBufUtil.hexDump(binaryRequest.getBytes()));
    final InetSocketAddress remoteAddress = getRemoteAddress(ctx);
    if (remoteAddress
        != null) { // binary protocol is only supported for proxies request and not mocking
      sendMessage(ctx, binaryRequest, logCorrelationId, remoteAddress);
    } else {
      log.info(
          "unknown message format, only HTTP requests are supported for mocking or HTTP &"
              + " binary requests for proxying, but request is not being proxied and"
              + " request is not valid HTTP, found request in binary: {} in utf8 text:"
              + " {}",
          ByteBufUtil.hexDump(binaryRequest.getBytes()),
          new String(binaryRequest.getBytes(), StandardCharsets.UTF_8));
      ctx.writeAndFlush(
          Unpooled.copiedBuffer(
              "unknown message format, only HTTP requests are supported for mocking or HTTP & binary requests for proxying, but request is not being proxied and request is not valid HTTP"
                  .getBytes(StandardCharsets.UTF_8)));
      ctx.close();
    }
  }

  private void sendMessage(
      ChannelHandlerContext ctx,
      BinaryMessage binaryRequest,
      String logCorrelationId,
      InetSocketAddress remoteAddress) {
    CompletableFuture<BinaryMessage> binaryResponseFuture =
        httpClient.sendRequest(
            binaryRequest,
            isSslEnabledUpstream(ctx.channel()),
            remoteAddress,
            configuration.socketConnectionTimeoutInMillis());

    if (configuration.forwardBinaryRequestsWithoutWaitingForResponse()) {
      processNotWaitingForResponse(
          ctx, binaryRequest, logCorrelationId, remoteAddress, binaryResponseFuture);
    } else {
      processWaitingForResponse(
          ctx, binaryRequest, logCorrelationId, remoteAddress, binaryResponseFuture);
    }
  }

  private void processNotWaitingForResponse(
      ChannelHandlerContext ctx,
      BinaryMessage binaryRequest,
      String logCorrelationId,
      InetSocketAddress remoteAddress,
      CompletableFuture<BinaryMessage> binaryResponseFuture) {
    if (binaryExchangeCallback != null) {
      binaryExchangeCallback.onProxy(
          binaryRequest, binaryResponseFuture, remoteAddress, ctx.channel().remoteAddress());
    }
    scheduler.submit(
        binaryResponseFuture,
        () -> {
          try {
            BinaryMessage binaryResponse =
                binaryResponseFuture.get(configuration.maxFutureTimeoutInMillis(), MILLISECONDS);
            if (binaryResponse != null) {
              log.info(
                  "returning binary response:{}from:{}for forwarded binary request:{}",
                  formatBytes(binaryResponse.getBytes()),
                  remoteAddress,
                  formatBytes(binaryRequest.getBytes()));
              ctx.writeAndFlush(Unpooled.copiedBuffer(binaryResponse.getBytes()));
            }
          } catch (RuntimeException
              | InterruptedException
              | ExecutionException
              | TimeoutException e) {
            if (e instanceof InterruptedException) {
              Thread.currentThread().interrupt();
            }
            log.warn(
                "exception {} sending hex {} to {} closing connection",
                e.getMessage(),
                ByteBufUtil.hexDump(binaryRequest.getBytes()),
                remoteAddress);
            ctx.close();
          }
        },
        false);
  }

  private void processWaitingForResponse(
      ChannelHandlerContext ctx,
      BinaryMessage binaryRequest,
      String logCorrelationId,
      InetSocketAddress remoteAddress,
      CompletableFuture<BinaryMessage> binaryResponseFuture) {
    scheduler.submit(
        binaryResponseFuture,
        () -> {
          try {
            BinaryMessage binaryResponse =
                binaryResponseFuture.get(configuration.maxFutureTimeoutInMillis(), MILLISECONDS);
            log.info(
                "returning binary response:{}from:{}for forwarded binary request:{}",
                formatBytes(binaryResponse.getBytes()),
                remoteAddress,
                formatBytes(binaryRequest.getBytes()));
            if (binaryExchangeCallback != null) {
              binaryExchangeCallback.onProxy(
                  binaryRequest,
                  binaryResponseFuture,
                  remoteAddress,
                  ctx.channel().remoteAddress());
            }
            ctx.writeAndFlush(Unpooled.copiedBuffer(binaryResponse.getBytes()));
          } catch (Exception e) {
            if (e instanceof InterruptedException) {
              Thread.currentThread().interrupt();
            }
            log.warn(
                "exception {} sending hex{}to{}closing connection",
                e.getMessage(),
                ByteBufUtil.hexDump(binaryRequest.getBytes()),
                remoteAddress,
                e);
            ctx.close();
          }
        },
        false);
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
          this.getClass(),
          ctx.channel(),
          cause);
    }
    closeOnFlush(ctx.channel());
  }
}
