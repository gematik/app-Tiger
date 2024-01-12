package de.gematik.test.tiger.mockserver.codec;

import de.gematik.test.tiger.mockserver.configuration.Configuration;
import de.gematik.test.tiger.mockserver.logging.MockServerLogger;
import io.netty.channel.CombinedChannelDuplexHandler;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.cert.Certificate;

public class MockServerHttpServerCodec
    extends CombinedChannelDuplexHandler<
        NettyHttpToMockServerHttpRequestDecoder, MockServerHttpToNettyHttpResponseEncoder> {

  public MockServerHttpServerCodec(
      Configuration configuration,
      MockServerLogger mockServerLogger,
      boolean isSecure,
      Certificate[] clientCertificates,
      SocketAddress socketAddress) {
    this(
        configuration,
        mockServerLogger,
        isSecure,
        clientCertificates,
        socketAddress instanceof InetSocketAddress
            ? ((InetSocketAddress) socketAddress).getPort()
            : null);
  }

  public MockServerHttpServerCodec(
      Configuration configuration,
      MockServerLogger mockServerLogger,
      boolean isSecure,
      Certificate[] clientCertificates,
      Integer port) {
    init(
        new NettyHttpToMockServerHttpRequestDecoder(
            configuration, mockServerLogger, isSecure, clientCertificates, port),
        new MockServerHttpToNettyHttpResponseEncoder(mockServerLogger));
  }
}
