/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.codec;

import static de.gematik.test.tiger.mockserver.socket.tls.SniHandler.getALPNProtocol;

import de.gematik.test.tiger.mockserver.configuration.Configuration;
import de.gematik.test.tiger.mockserver.mappers.FullHttpRequestToMockServerHttpRequest;
import de.gematik.test.tiger.mockserver.model.Header;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.FullHttpRequest;
import java.net.SocketAddress;
import java.security.cert.Certificate;
import java.util.List;

/*
 * @author jamesdbloom
 */
public class NettyHttpToMockServerHttpRequestDecoder
    extends MessageToMessageDecoder<FullHttpRequest> {

  private final FullHttpRequestToMockServerHttpRequest fullHttpRequestToMockServerRequest;

  public NettyHttpToMockServerHttpRequestDecoder(
      Configuration configuration,
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
    if (ctx != null && ctx.channel() != null) {
      preservedHeaders = PreserveHeadersNettyRemoves.preservedHeaders(ctx.channel());
      localAddress = ctx.channel().localAddress();
      remoteAddress = ctx.channel().remoteAddress();
    }
    out.add(
        fullHttpRequestToMockServerRequest.mapFullHttpRequestToMockServerRequest(
            fullHttpRequest,
            preservedHeaders,
            localAddress,
            remoteAddress,
            getALPNProtocol(ctx)));
  }
}
