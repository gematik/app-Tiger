/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.netty;

import de.gematik.test.tiger.proxy.data.TigerConnectionStatus;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class ConnectionCounterHandler extends ChannelInboundHandlerAdapter {

  private final MockServer mockServer;

  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    mockServer.addConnectionWithStatus(
        ctx.channel().remoteAddress(), TigerConnectionStatus.OPEN_TCP);
  }

  @Override
  public void channelUnregistered(ChannelHandlerContext ctx) {
    mockServer.removeRemoteAddress(ctx.channel().remoteAddress());
  }
}
