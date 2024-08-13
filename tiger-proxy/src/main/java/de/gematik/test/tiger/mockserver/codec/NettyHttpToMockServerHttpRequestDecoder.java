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

import static de.gematik.test.tiger.mockserver.socket.tls.SniHandler.getALPNProtocol;

import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.mappers.FullHttpRequestToMockServerHttpRequest;
import de.gematik.test.tiger.mockserver.model.Header;
import de.gematik.test.tiger.mockserver.socket.tls.SniHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.FullHttpRequest;
import java.net.SocketAddress;
import java.security.cert.Certificate;
import java.util.List;
import javax.net.ssl.SSLSession;

/*
 * @author jamesdbloom
 */
public class NettyHttpToMockServerHttpRequestDecoder
    extends MessageToMessageDecoder<FullHttpRequest> {

  private final FullHttpRequestToMockServerHttpRequest fullHttpRequestToMockServerRequest;

  public NettyHttpToMockServerHttpRequestDecoder(
      MockServerConfiguration configuration,
      boolean isSecure,
      Certificate[] clientCertificates,
      Integer port) {
    this.fullHttpRequestToMockServerRequest =
        new FullHttpRequestToMockServerHttpRequest(
            configuration, isSecure, clientCertificates, port);
  }

  @Override
  protected void decode(
      ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, List<Object> out) {
    List<Header> preservedHeaders = null;
    SocketAddress localAddress = null;
    SocketAddress remoteAddress = null;
    SSLSession sslSession = null;
    if (ctx != null && ctx.channel() != null) {
      preservedHeaders = PreserveHeadersNettyRemoves.preservedHeaders(ctx.channel());
      localAddress = ctx.channel().localAddress();
      remoteAddress = ctx.channel().remoteAddress();
      sslSession = ctx.channel().attr(SniHandler.SSL_SESSION).get();
    }
    out.add(
        fullHttpRequestToMockServerRequest.mapFullHttpRequestToMockServerRequest(
            fullHttpRequest,
            preservedHeaders,
            localAddress,
            remoteAddress,
            getALPNProtocol(ctx),
            sslSession));
  }
}
