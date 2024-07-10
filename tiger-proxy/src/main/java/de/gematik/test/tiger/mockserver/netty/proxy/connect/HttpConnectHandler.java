/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.netty.proxy.connect;

import static de.gematik.test.tiger.mockserver.model.HttpResponse.response;

import de.gematik.test.tiger.mockserver.codec.MockServerHttpServerCodec;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.lifecycle.LifeCycle;
import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.netty.proxy.relay.RelayConnectHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;

/*
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public final class HttpConnectHandler extends RelayConnectHandler<HttpRequest> {

  public HttpConnectHandler(MockServerConfiguration configuration, LifeCycle server, String host, int port) {
    super(configuration, server, host, port);
  }

  protected void removeCodecSupport(ChannelHandlerContext ctx) {
    ChannelPipeline pipeline = ctx.pipeline();
    removeHandler(pipeline, HttpServerCodec.class);
    removeHandler(pipeline, HttpContentDecompressor.class);
    removeHandler(pipeline, HttpObjectAggregator.class);
    removeHandler(pipeline, MockServerHttpServerCodec.class);
    if (pipeline.get(this.getClass()) != null) {
      pipeline.remove(this);
    }
  }

  protected Object successResponse(Object request) {
    return response();
  }

  protected Object failureResponse(Object request) {
    return response().withStatusCode(HttpResponseStatus.BAD_GATEWAY.code());
  }
}
