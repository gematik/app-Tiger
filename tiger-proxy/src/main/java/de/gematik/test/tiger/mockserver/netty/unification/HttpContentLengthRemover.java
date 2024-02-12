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

package de.gematik.test.tiger.mockserver.netty.unification;

import com.google.common.net.HttpHeaders;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.DefaultHttpMessage;
import io.netty.util.ReferenceCountUtil;
import java.util.List;

/*
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
public class HttpContentLengthRemover extends MessageToMessageEncoder<DefaultHttpMessage> {
  @Override
  @SuppressWarnings("unchecked")
  protected void encode(
      ChannelHandlerContext ctx, DefaultHttpMessage defaultHttpMessage, List out) {
    if (defaultHttpMessage.headers().contains(HttpHeaders.CONTENT_LENGTH, "", true)) {
      defaultHttpMessage.headers().remove(HttpHeaders.CONTENT_LENGTH);
    }
    ReferenceCountUtil.retain(defaultHttpMessage);
    out.add(defaultHttpMessage);
  }
}
