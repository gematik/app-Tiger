package de.gematik.test.tiger.mockserver.codec;

import static de.gematik.test.tiger.mockserver.model.BinaryMessage.bytes;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;

public class NettyBinaryToMockServerBinaryResponseDecoder extends MessageToMessageDecoder<ByteBuf> {
  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> out) {
    out.add(bytes(ByteBufUtil.getBytes(byteBuf)));
  }
}
