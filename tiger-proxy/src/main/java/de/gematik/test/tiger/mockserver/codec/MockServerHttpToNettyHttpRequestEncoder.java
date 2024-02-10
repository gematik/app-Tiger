/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.codec;

import de.gematik.test.tiger.mockserver.mappers.MockServerHttpRequestToFullHttpRequest;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;
import java.util.Map;

/*
 * @author jamesdbloom
 */
public class MockServerHttpToNettyHttpRequestEncoder extends MessageToMessageEncoder<HttpRequest> {

  private final MockServerHttpRequestToFullHttpRequest mockServerHttpRequestToFullHttpRequest;

  MockServerHttpToNettyHttpRequestEncoder(
      Map<ProxyConfiguration.Type, ProxyConfiguration> proxyConfigurations) {
    mockServerHttpRequestToFullHttpRequest =
        new MockServerHttpRequestToFullHttpRequest(proxyConfigurations);
  }

  @Override
  protected void encode(ChannelHandlerContext ctx, HttpRequest httpRequest, List<Object> out) {
    out.add(mockServerHttpRequestToFullHttpRequest.mapMockServerRequestToNettyRequest(httpRequest));
  }
}
