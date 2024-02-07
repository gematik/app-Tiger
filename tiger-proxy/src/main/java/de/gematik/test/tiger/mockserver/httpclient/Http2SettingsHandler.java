/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.httpclient;

import de.gematik.test.tiger.mockserver.model.Protocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http2.Http2Settings;
import java.util.concurrent.CompletableFuture;

/*
 * @author jamesdbloom
 */

/** Reads the first {@link Http2Settings} object */
public class Http2SettingsHandler extends SimpleChannelInboundHandler<Http2Settings> {
  private final CompletableFuture<Http2Settings> settingsFuture;

  public Http2SettingsHandler(CompletableFuture<Protocol> protocolFuture) {
    this.settingsFuture = new CompletableFuture<>();
    settingsFuture.whenComplete(
        ((http2Settings, throwable) -> {
          if (throwable != null) {
            protocolFuture.completeExceptionally(throwable);
          } else if (http2Settings != null) {
            protocolFuture.complete(Protocol.HTTP_2);
          } else {
            protocolFuture.complete(Protocol.HTTP_1_1);
          }
        }));
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Http2Settings http2Settings)
      throws Exception {
    settingsFuture.complete(http2Settings);

    // Only care about the first settings message
    ctx.pipeline().remove(this);
  }
}
