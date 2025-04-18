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

package de.gematik.test.tiger.mockserver.netty.proxy;

import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.closeOnFlush;
import static de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler.getRemoteAddress;
import static de.gematik.test.tiger.mockserver.model.BinaryMessage.bytes;
import static de.gematik.test.tiger.mockserver.netty.unification.PortUnificationHandler.isSslEnabledUpstream;

import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.httpclient.BinaryRequestInfo;
import de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient;
import de.gematik.test.tiger.mockserver.model.BinaryMessage;
import de.gematik.test.tiger.proxy.handler.BinaryExchangeHandler;
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
  private final BinaryExchangeHandler binaryExchangeCallback;

  public BinaryHandler(
      final MockServerConfiguration configuration, final NettyHttpClient httpClient) {
    super(true);
    this.httpClient = httpClient;
    this.binaryExchangeCallback = configuration.binaryProxyListener();
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf) {
    BinaryMessage binaryRequest = bytes(ByteBufUtil.getBytes(byteBuf));
    log.atDebug()
        .addArgument(() -> ByteBufUtil.hexDump(binaryRequest.getBytes()))
        .log("received binary request: {}");
    final InetSocketAddress remoteAddress = getRemoteAddress(ctx);
    if (remoteAddress != null) {
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
    binaryExchangeCallback.propagateExceptionMessageSafe(
        cause, RbelHostname.create(ctx.channel().remoteAddress()), null);
    closeOnFlush(ctx.channel());
  }
}
