/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.mockserver.netty;

import de.gematik.test.tiger.mockserver.configuration.Configuration;
import de.gematik.test.tiger.mockserver.mock.HttpState;
import de.gematik.test.tiger.mockserver.mock.action.http.HttpActionHandler;
import de.gematik.test.tiger.mockserver.netty.unification.PortUnificationHandler;
import de.gematik.test.tiger.mockserver.socket.tls.NettySslContextFactory;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
@Data
@Slf4j
public class MockServerUnificationInitializer extends ChannelHandlerAdapter {
  private final Configuration configuration;
  private final MockServer server;
  private final HttpState httpState;
  private final HttpActionHandler actionHandler;
  private final NettySslContextFactory nettySslContextFactory;

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) {
    ctx.pipeline()
        .addFirst(new ConnectionCounterHandler(server))
        .replace(
            this,
            null,
            new PortUnificationHandler(
                configuration, server, httpState, actionHandler, nettySslContextFactory));
  }
}
