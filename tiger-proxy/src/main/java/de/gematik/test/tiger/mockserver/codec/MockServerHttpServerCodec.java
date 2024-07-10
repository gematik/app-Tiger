/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.codec;

import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import io.netty.channel.CombinedChannelDuplexHandler;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.cert.Certificate;

/*
 * @author jamesdbloom
 */
public class MockServerHttpServerCodec
    extends CombinedChannelDuplexHandler<
        NettyHttpToMockServerHttpRequestDecoder, MockServerHttpToNettyHttpResponseEncoder> {

  public MockServerHttpServerCodec(
      MockServerConfiguration configuration,
      boolean isSecure,
      Certificate[] clientCertificates,
      SocketAddress socketAddress) {
    this(
        configuration,
        isSecure,
        clientCertificates,
        socketAddress instanceof InetSocketAddress
            ? ((InetSocketAddress) socketAddress).getPort()
            : null);
  }

  public MockServerHttpServerCodec(
      MockServerConfiguration configuration,
      boolean isSecure,
      Certificate[] clientCertificates,
      Integer port) {
    init(
        new NettyHttpToMockServerHttpRequestDecoder(
            configuration, isSecure, clientCertificates, port),
        new MockServerHttpToNettyHttpResponseEncoder());
  }
}
