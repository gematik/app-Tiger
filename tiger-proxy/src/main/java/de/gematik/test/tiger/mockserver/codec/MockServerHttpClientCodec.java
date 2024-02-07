/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.codec;

import de.gematik.test.tiger.mockserver.logging.MockServerLogger;
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
      MockServerLogger mockServerLogger,
      Map<ProxyConfiguration.Type, ProxyConfiguration> proxyConfigurations) {
    init(
        new NettyHttpToMockServerHttpResponseDecoder(mockServerLogger),
        new MockServerHttpToNettyHttpRequestEncoder(mockServerLogger, proxyConfigurations));
  }
}
