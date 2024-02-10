/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.codec;

import de.gematik.test.tiger.mockserver.proxyconfiguration.ProxyConfiguration;
import io.netty.channel.CombinedChannelDuplexHandler;
import java.util.Map;

/*
 * @author jamesdbloom
 */
public class MockServerHttpClientCodec
    extends CombinedChannelDuplexHandler<
        NettyHttpToMockServerHttpResponseDecoder, MockServerHttpToNettyHttpRequestEncoder> {

  public MockServerHttpClientCodec(
      Map<ProxyConfiguration.Type, ProxyConfiguration> proxyConfigurations) {
    init(
        new NettyHttpToMockServerHttpResponseDecoder(),
        new MockServerHttpToNettyHttpRequestEncoder(proxyConfigurations));
  }
}
