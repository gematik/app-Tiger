/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.mockserver.httpclient;

import de.gematik.test.tiger.mockserver.model.HttpProtocol;
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

  public Http2SettingsHandler(CompletableFuture<HttpProtocol> protocolFuture) {
    this.settingsFuture = new CompletableFuture<>();
    settingsFuture.whenComplete(
        ((http2Settings, throwable) -> {
          if (throwable != null) {
            protocolFuture.completeExceptionally(throwable);
          } else if (http2Settings != null) {
            protocolFuture.complete(HttpProtocol.HTTP_2);
          } else {
            protocolFuture.complete(HttpProtocol.HTTP_1_1);
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
