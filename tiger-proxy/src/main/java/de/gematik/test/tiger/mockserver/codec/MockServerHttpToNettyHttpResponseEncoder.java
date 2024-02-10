/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.codec;

import de.gematik.test.tiger.mockserver.mappers.MockServerHttpResponseToFullHttpResponse;
import de.gematik.test.tiger.mockserver.model.HttpResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;

/*
 * @author jamesdbloom
 */
public class MockServerHttpToNettyHttpResponseEncoder
    extends MessageToMessageEncoder<HttpResponse> {

  private final MockServerHttpResponseToFullHttpResponse mockServerHttpResponseToFullHttpResponse = new MockServerHttpResponseToFullHttpResponse();

  @Override
  protected void encode(ChannelHandlerContext ctx, HttpResponse response, List<Object> out) {
    out.addAll(
        mockServerHttpResponseToFullHttpResponse.mapMockServerResponseToNettyResponse(response));
  }
}
