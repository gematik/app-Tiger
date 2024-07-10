/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.netty;

import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.mock.HttpState;
import de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler;
import de.gematik.test.tiger.mockserver.netty.unification.PortUnificationHandler;
import de.gematik.test.tiger.mockserver.socket.tls.NettySslContextFactory;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@EqualsAndHashCode(callSuper = false)
@ChannelHandler.Sharable
@Data
@Slf4j
public class MockServerUnificationInitializer extends ChannelHandlerAdapter {
  private final MockServerConfiguration configuration;
  private final MockServer server;
  private final HttpState httpState;
  private final HttpActionHandler actionHandler;
  private final NettySslContextFactory nettySslContextFactory;

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) {
    ctx.pipeline()
        .replace(
            this,
            null,
            new PortUnificationHandler(
                configuration, server, httpState, actionHandler, nettySslContextFactory));
  }
}
