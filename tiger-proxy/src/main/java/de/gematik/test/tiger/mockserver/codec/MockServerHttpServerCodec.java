/*
 * Copyright 2024 gematik GmbH
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
