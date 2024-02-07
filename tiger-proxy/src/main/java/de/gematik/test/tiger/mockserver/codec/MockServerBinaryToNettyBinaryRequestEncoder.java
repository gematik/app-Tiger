/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.codec;

import de.gematik.test.tiger.mockserver.model.BinaryMessage;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;

/*
 * @author jamesdbloom
 */
public class MockServerBinaryToNettyBinaryRequestEncoder
    extends MessageToMessageEncoder<BinaryMessage> {
  @Override
  protected void encode(ChannelHandlerContext ctx, BinaryMessage binaryMessage, List<Object> out) {
    out.add(Unpooled.copiedBuffer(binaryMessage.getBytes()));
  }
}
