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

package de.gematik.test.tiger.mockserver.httpclient;

import static de.gematik.test.tiger.mockserver.httpclient.NettyHttpClient.RESPONSE_FUTURE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import de.gematik.test.tiger.mockserver.model.Message;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.NotSslRecordException;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.SSLException;

/*
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class HttpClientHandler extends SimpleChannelInboundHandler<Message> {

  private final List<String> connectionClosedStrings =
      Arrays.asList("Broken pipe", "(broken pipe)", "Connection reset");

  HttpClientHandler() {
    super(false);
  }

  @Override
  public void channelRead0(ChannelHandlerContext ctx, Message response) {
    ctx.channel().attr(RESPONSE_FUTURE).get().complete(response);
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    ctx.close();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    if (isNotSslException(cause) && isNotConnectionReset(cause)) {
      cause.printStackTrace(); // NOSONAR
    }
    ctx.channel().attr(RESPONSE_FUTURE).get().completeExceptionally(cause);
    ctx.close();
  }

  private boolean isNotSslException(Throwable cause) {
    return !(cause.getCause() instanceof SSLException
        || cause instanceof DecoderException
        || cause instanceof NotSslRecordException);
  }

  private boolean isNotConnectionReset(Throwable cause) {
    return connectionClosedStrings.stream()
        .noneMatch(
            connectionClosedString ->
                (isNotBlank(cause.getMessage())
                        && cause.getMessage().contains(connectionClosedString))
                    || (cause.getCause() != null
                        && isNotBlank(cause.getCause().getMessage())
                        && cause.getCause().getMessage().contains(connectionClosedString)));
  }
}
