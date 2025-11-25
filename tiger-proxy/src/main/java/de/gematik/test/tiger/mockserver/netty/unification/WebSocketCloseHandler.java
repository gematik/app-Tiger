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
package de.gematik.test.tiger.mockserver.netty.unification;

import de.gematik.test.tiger.mockserver.model.BinaryMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;

@Slf4j
public class WebSocketCloseHandler extends SimpleChannelInboundHandler<BinaryMessage> {

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, BinaryMessage msg) {
    log.info("Received BinaryRequestInfo: {}", Hex.toHexString(msg.getBytes()));
    if (isCloseFrame(msg)) {
      log.info("WebSocket Close frame detected, closing channel.");
      ctx.channel().writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE);
    }
    ctx.fireChannelRead(msg);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    if (cause instanceof java.net.SocketException
        && "Connection reset".equals(cause.getMessage())) {
      return;
    }
    super.exceptionCaught(ctx, cause);
  }

  public static boolean isCloseFrame(BinaryMessage msg) {
    if (msg.getBytes().length < 2) {
      return false;
    }

    byte b0 = msg.getBytes()[0];
    byte opcode = (byte) (b0 & 0x0F);

    // opcode 0x8 = CLOSE
    return opcode == 0x8;
  }

  public static void safeRelease(ByteBuf buf) {
    ReferenceCountUtil.safeRelease(buf);
  }
}
