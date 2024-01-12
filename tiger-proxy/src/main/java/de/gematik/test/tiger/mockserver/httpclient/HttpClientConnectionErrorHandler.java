package de.gematik.test.tiger.mockserver.httpclient;

import static de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient.ERROR_IF_CHANNEL_CLOSED_WITHOUT_RESPONSE;
import static de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient.RESPONSE_FUTURE;

import de.gematik.test.tiger.mockserver.model.Message;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import java.util.concurrent.CompletableFuture;

@ChannelHandler.Sharable
public class HttpClientConnectionErrorHandler extends ChannelDuplexHandler {

  @Override
  public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
    CompletableFuture<? extends Message> responseFuture = ctx.channel().attr(RESPONSE_FUTURE).get();
    if (responseFuture != null && !responseFuture.isDone()) {
      if (ctx.channel().attr(ERROR_IF_CHANNEL_CLOSED_WITHOUT_RESPONSE).get()) {
        responseFuture.completeExceptionally(
            new SocketConnectionException(
                "Channel handler removed before valid response has been received"));
      } else {
        responseFuture.complete(null);
      }
    }
    super.handlerRemoved(ctx);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    CompletableFuture<? extends Message> responseFuture = ctx.channel().attr(RESPONSE_FUTURE).get();
    if (!responseFuture.isDone()) {
      responseFuture.completeExceptionally(cause);
    }
    super.exceptionCaught(ctx, cause);
  }
}
