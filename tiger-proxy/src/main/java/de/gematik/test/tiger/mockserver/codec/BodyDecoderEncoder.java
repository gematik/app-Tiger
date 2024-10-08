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

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import de.gematik.test.tiger.mockserver.model.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.Charset;

/*
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public class BodyDecoderEncoder {

  public ByteBuf bodyToByteBuf(Body body) {
    if (body != null) {
      final byte[] rawBytes = body.getRawBytes();
      if (rawBytes != null) {
        return Unpooled.copiedBuffer(rawBytes);
      }
    }
    return Unpooled.buffer(0, 0);
  }

  public ByteBuf[] bodyToByteBuf(Body body, String contentTypeHeader, int chunkSize) {
    byte[][] chunks = split(bodyToBytes(body, contentTypeHeader), chunkSize);
    ByteBuf[] byteBufs = new ByteBuf[chunks.length];
    for (int i = 0; i < chunks.length; i++) {
      if (chunks[i] != null) {
        byteBufs[i] = Unpooled.copiedBuffer(chunks[i]);
      } else {
        byteBufs[i] = Unpooled.buffer(0, 0);
      }
    }
    return byteBufs;
  }

  public static byte[][] split(byte[] array, int chunkSize) {
    if (chunkSize < array.length) {
      int numOfChunks = (array.length + chunkSize - 1) / chunkSize;
      byte[][] output = new byte[numOfChunks][];

      for (int i = 0; i < numOfChunks; ++i) {
        int start = i * chunkSize;
        int length = Math.min(array.length - start, chunkSize);

        byte[] temp = new byte[length];
        System.arraycopy(array, start, temp, 0, length);
        output[i] = temp;
      }
      return output;
    } else {
      return new byte[][] {array};
    }
  }

  byte[] bodyToBytes(Body body, String contentTypeHeader) {
    if (body != null) {
      if (body instanceof BinaryBody) {
        return body.getRawBytes();
      } else if (body.getValue() instanceof String) {
        Charset contentTypeCharset = MediaType.parse(contentTypeHeader).getCharsetOrDefault();
        Charset bodyCharset = body.getCharset(contentTypeCharset);
        return ((String) body.getValue())
            .getBytes(
                bodyCharset != null ? bodyCharset : MediaType.DEFAULT_TEXT_HTTP_CHARACTER_SET);
      } else {
        return body.getRawBytes();
      }
    }
    return null;
  }

  public BodyWithContentType byteBufToBody(ByteBuf content, String contentTypeHeader) {
    if (content != null && content.readableBytes() > 0) {
      byte[] bodyBytes = new byte[content.readableBytes()];
      content.readBytes(bodyBytes);
      return bytesToBody(bodyBytes, contentTypeHeader);
    }
    return null;
  }

  public BodyWithContentType bytesToBody(byte[] bodyBytes, String contentTypeHeader) {
    if (bodyBytes.length > 0) {
      MediaType mediaType = MediaType.parse(contentTypeHeader);
      if (mediaType.isString()) {
        return new StringBody(
            new String(bodyBytes, mediaType.getCharsetOrDefault()),
            bodyBytes,
            false,
            isNotBlank(contentTypeHeader) ? mediaType : null);
      } else {
        return new BinaryBody(bodyBytes, mediaType);
      }
    }
    return null;
  }
}
