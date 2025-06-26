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
package de.gematik.test.tiger.mockserver.codec;

import de.gematik.test.tiger.mockserver.model.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/*
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public class BodyDecoderEncoder {

  public ByteBuf bodyToByteBuf(byte[] body) {
    if (body != null) {
      return Unpooled.copiedBuffer(body);
    }
    return Unpooled.buffer(0, 0);
  }

  public byte[] byteBufToBody(ByteBuf content) {
    if (content != null && content.readableBytes() > 0) {
      byte[] bodyBytes = new byte[content.readableBytes()];
      content.readBytes(bodyBytes);
      return bodyBytes;
    }
    return new byte[0];
  }
}
