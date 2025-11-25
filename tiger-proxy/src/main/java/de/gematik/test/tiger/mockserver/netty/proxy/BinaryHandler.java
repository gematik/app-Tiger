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
package de.gematik.test.tiger.mockserver.netty.proxy;

import static de.gematik.test.tiger.mockserver.exception.ExceptionHandling.closeOnFlush;
import static de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler.getRemoteAddress;
import static de.gematik.test.tiger.mockserver.model.BinaryMessage.bytes;
import static de.gematik.test.tiger.mockserver.netty.unification.PortUnificationHandler.isSslEnabledUpstream;

import de.gematik.rbellogger.data.RbelMessageKind;
import de.gematik.rbellogger.util.RbelSocketAddress;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.httpclient.BinaryBridgeHandler;
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
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
@Slf4j
public class BinaryHandler extends SimpleChannelInboundHandler<ByteBuf> {

  private final NettyHttpClient httpClient;
  private final BinaryExchangeHandler binaryExchangeCallback;
  private final BinaryModifierApplier binaryModifierApplier;

  public BinaryHandler(
      final MockServerConfiguration configuration, final NettyHttpClient httpClient) {
    super(true);
    this.httpClient = httpClient;
    this.binaryExchangeCallback = configuration.binaryProxyListener();
    this.binaryModifierApplier = new BinaryModifierApplier(configuration);
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf) {
    BinaryMessage binaryRequest = bytes(ByteBufUtil.getBytes(byteBuf));
    final InetSocketAddress remoteAddress = getRemoteAddress(ctx);
    if (ctx.channel().attr(BinaryBridgeHandler.OUTGOING_CHANNEL).get() != null) {
      binaryModifierApplier
          .applyModifierPlugins(binaryRequest, ctx, RbelMessageKind.REQUEST)
          .forEach(
              msg ->
                  sendMessage(
                      BinaryRequestInfo.builder()
                          .incomingChannel(ctx.channel())
                          .outgoingChannel(
                              ctx.channel().attr(BinaryBridgeHandler.OUTGOING_CHANNEL).get())
                          .remoteServerAddress(remoteAddress)
                          .dataToSend(msg)
                          .build()));
    } else if (remoteAddress != null) {
      binaryModifierApplier
          .applyModifierPlugins(binaryRequest, ctx, RbelMessageKind.REQUEST)
          .forEach(msg -> sendMessage(new BinaryRequestInfo(ctx.channel(), msg, remoteAddress)));
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

  private void sendMessage(BinaryRequestInfo binaryRequestInfo) {
    /*
     * see BinaryBridgeHandler.channelRead0
     */
    synchronized (binaryExchangeCallback) {
      httpClient
          .sendRequest(
              binaryRequestInfo, isSslEnabledUpstream(binaryRequestInfo.getIncomingChannel()))
          .exceptionally(
              throwable -> {
                binaryExchangeCallback.propagateExceptionMessageSafe(
                    throwable,
                    RbelSocketAddress.create(binaryRequestInfo.getRemoteServerAddress()),
                    RbelSocketAddress.create(
                        binaryRequestInfo.getIncomingChannel().remoteAddress()));
                return null;
              });

      processNotWaitingForResponse(binaryRequestInfo);
    }
  }

  private void processNotWaitingForResponse(BinaryRequestInfo binaryRequestInfo) {
    binaryExchangeCallback.onProxy(
        binaryRequestInfo.getDataToSend(),
        binaryRequestInfo.retrieveActualRemoteAddress(),
        binaryRequestInfo.getIncomingChannel().remoteAddress(),
        RbelMessageKind.REQUEST);
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) {
    ctx.flush();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    binaryExchangeCallback.propagateExceptionMessageSafe(
        cause, RbelSocketAddress.create(ctx.channel().remoteAddress()), null);
    closeOnFlush(ctx.channel());
  }
}
