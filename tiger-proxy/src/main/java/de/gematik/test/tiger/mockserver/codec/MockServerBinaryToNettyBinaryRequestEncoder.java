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

package de.gematik.test.tiger.mockserver.codec;

import de.gematik.test.tiger.mockserver.model.BinaryMessage;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;

/*
 * @author jamesdbloom
 */
public class MockServerBinaryToNettyBinaryRequestEncoder
    extends MessageToMessageEncoder<BinaryMessage> {
  @Override
  protected void encode(ChannelHandlerContext ctx, BinaryMessage binaryMessage, List<Object> out) {
    out.add(Unpooled.copiedBuffer(binaryMessage.getBytes()));
  }
}
