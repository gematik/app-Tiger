package de.gematik.test.tiger.mockserver.netty;

import de.gematik.test.tiger.mockserver.configuration.Configuration;
import de.gematik.test.tiger.mockserver.lifecycle.LifeCycle;
import de.gematik.test.tiger.mockserver.mock.HttpState;
import de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler;
import de.gematik.test.tiger.mockserver.netty.unification.PortUnificationHandler;
import de.gematik.test.tiger.mockserver.socket.tls.NettySslContextFactory;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;

@ChannelHandler.Sharable
public class MockServerUnificationInitializer extends ChannelHandlerAdapter {
  private final Configuration configuration;
  private final LifeCycle server;
  private final HttpState httpState;
  private final HttpActionHandler actionHandler;
  private final NettySslContextFactory nettySslContextFactory;

  public MockServerUnificationInitializer(
      Configuration configuration,
      LifeCycle server,
      HttpState httpState,
      HttpActionHandler actionHandler,
      NettySslContextFactory nettySslContextFactory) {
    this.configuration = configuration;
    this.server = server;
    this.httpState = httpState;
    this.actionHandler = actionHandler;
    this.nettySslContextFactory = nettySslContextFactory;
  }

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
