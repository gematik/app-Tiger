/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.netty.proxy;

import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.closeOnFlush;
import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.connectionClosedException;
import static de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler.getRemoteAddress;
import static de.gematik.test.tiger.mockserver.model.BinaryMessage.bytes;
import static de.gematik.test.tiger.mockserver.netty.unification.PortUnificationHandler.isSslEnabledUpstream;

import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.httpclient.BinaryRequestInfo;
import de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient;
import de.gematik.test.tiger.mockserver.model.BinaryMessage;
import de.gematik.test.tiger.mockserver.model.BinaryProxyListener;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
@Slf4j
public class BinaryHandler extends SimpleChannelInboundHandler<ByteBuf> {

  private final NettyHttpClient httpClient;
  private final BinaryProxyListener binaryExchangeCallback;

  public BinaryHandler(final MockServerConfiguration configuration, final NettyHttpClient httpClient) {
    super(true);
    this.httpClient = httpClient;
    this.binaryExchangeCallback = configuration.binaryProxyListener();
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf) {
    BinaryMessage binaryRequest = bytes(ByteBufUtil.getBytes(byteBuf));
    final InetSocketAddress remoteAddress = getRemoteAddress(ctx);
    if (remoteAddress
        != null) { // binary protocol is only supported for proxies request and not mocking
      sendMessage(new BinaryRequestInfo(ctx.channel(), binaryRequest, remoteAddress));
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

  public void sendMessage(BinaryRequestInfo binaryRequestInfo) {
    CompletableFuture<BinaryMessage> binaryResponseFuture =
        httpClient.sendRequest(
            binaryRequestInfo, isSslEnabledUpstream(binaryRequestInfo.getIncomingChannel()));

    processNotWaitingForResponse(binaryRequestInfo, binaryResponseFuture);
  }

  private void processNotWaitingForResponse(
      BinaryRequestInfo binaryRequestInfo, CompletableFuture<BinaryMessage> binaryResponseFuture) {
    if (binaryExchangeCallback != null) {
      binaryExchangeCallback.onProxy(
          binaryRequestInfo.getDataToSend(),
          Optional.of(binaryResponseFuture),
          binaryRequestInfo.getRemoteServerAddress(),
          binaryRequestInfo.getIncomingChannel().remoteAddress());
    }
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
